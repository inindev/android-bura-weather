/*
 * Copyright 2024 David Takač
 *
 * This file is part of Bura.
 *
 * Bura is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Bura is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Bura. If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidtakac.bura.forecast

import com.davidtakac.bura.forecast.cache.ForecastCacher
import com.davidtakac.bura.forecast.download.ForecastDownloader
import com.davidtakac.bura.places.Coordinates
import com.davidtakac.bura.forecast.units.Units
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ForecastRepository(
    private val cacher: ForecastCacher,
    private val downloader: ForecastDownloader
) {
    // Callers race here from the main thread (view models) and IO threads (widget broadcasts), so
    // the map must be concurrent: a plain getOrPut can hand two callers different mutexes for the
    // same coords, silently losing the mutual exclusion the mutex exists to provide.
    private val coordsToMutex = ConcurrentHashMap<Coordinates, Mutex>()

    suspend fun get(
        coords: Coordinates,
        units: Units,
        updatePolicy: UpdatePolicy = UpdatePolicy.Eager
    ): Forecast? = when (val result = getResult(coords, units, updatePolicy)) {
        is ForecastResult.Fresh -> result.forecast
        is ForecastResult.Stale -> result.forecast
        ForecastResult.None -> null
    }

    /**
     * Like [get], but reports whether the forecast is [ForecastResult.Fresh] (served from the network,
     * or from a cache still within its freshness window) or [ForecastResult.Stale] (the network fetch
     * failed and the last cached forecast is served instead). Callers that show an "up to date" vs
     * "couldn't refresh" cue use this. The forecast's [Forecast.timestamp] is set only on a successful
     * download, so a stale result carries the last-success time — the displayed update time never
     * advances on a failed refresh.
     */
    suspend fun getResult(
        coords: Coordinates,
        units: Units,
        updatePolicy: UpdatePolicy = UpdatePolicy.Eager
    ): ForecastResult {
        // Cache reads are lock-free: only the download-and-save below is serialized per coords.
        // Locking the whole method (the old shape) made even Static repaints queue behind an
        // in-flight network fetch for the same place, so a hung download blocked the cache-only
        // path that exists to repaint the widget when a fetch runs long.
        val cached = cacher.get(coords)
        if (updatePolicy == UpdatePolicy.Static) {
            return (cached?.let { ForecastResult.Fresh(it) } ?: ForecastResult.None)
                .convertTo(units)
        }
        if (cached != null && !shouldUpdate(cached.timestamp, updatePolicy)) {
            return ForecastResult.Fresh(cached).convertTo(units)
        }
        return coordsToMutex.computeIfAbsent(coords) { Mutex() }.withLock {
            // Re-check freshness: another caller may have refreshed while we waited on the lock,
            // in which case its result is reused instead of downloading again.
            val current = cacher.get(coords)
            if (current != null && !shouldUpdate(current.timestamp, updatePolicy)) {
                ForecastResult.Fresh(current)
            } else {
                val downloaded = downloader.get(coords)
                if (downloaded == null) {
                    if (current != null) ForecastResult.Stale(current) else ForecastResult.None
                } else {
                    cacher.save(coords, downloaded)
                    ForecastResult.Fresh(downloaded)
                }
            }
        }.convertTo(units)
    }

    private fun ForecastResult.convertTo(units: Units): ForecastResult = when (this) {
        is ForecastResult.Fresh -> ForecastResult.Fresh(forecast.convertTo(units))
        is ForecastResult.Stale -> ForecastResult.Stale(forecast.convertTo(units))
        ForecastResult.None -> ForecastResult.None
    }

    private fun shouldUpdate(timestamp: Instant, updatePolicy: UpdatePolicy): Boolean =
        when (updatePolicy) {
            UpdatePolicy.Static -> false
            UpdatePolicy.Force -> true
            else -> Duration.between(timestamp, Instant.now()) >= when (updatePolicy) {
                UpdatePolicy.Wake -> Duration.ofMinutes(30)
                UpdatePolicy.Eager -> Duration.ofHours(1)
                else -> Duration.ofHours(6)
            }
        }
}

enum class UpdatePolicy {
    /** Refresh if the cache is older than 30 min. Used by the widget's presence-gated wake refresh. */
    Wake,
    Eager, Frugal,

    /**
     * Strictly cache-only: never downloads and never waits on an in-flight download, even when
     * nothing is cached. The widget uses this to repaint deterministically (e.g. to clear the
     * refresh spinner) while a fetch may be stuck.
     */
    Static,
    Force
}

/** Outcome of a forecast request: fresh data, stale cache after a failed fetch, or nothing at all. */
sealed interface ForecastResult {
    data class Fresh(val forecast: Forecast) : ForecastResult
    data class Stale(val forecast: Forecast) : ForecastResult
    data object None : ForecastResult
}