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

class ForecastRepository(
    private val cacher: ForecastCacher,
    private val downloader: ForecastDownloader
) {
    private val coordsToMutex = mutableMapOf<Coordinates, Mutex>()

    suspend fun get(
        coords: Coordinates,
        units: Units,
        updatePolicy: UpdatePolicy = UpdatePolicy.Eager
    ): Forecast? =
        coordsToMutex.getOrPut(coords, defaultValue = { Mutex() }).withLock {
            val cached = cacher.get(coords)
            if (cached == null || shouldUpdate(cached.timestamp, updatePolicy)) {
                val downloaded = downloader.get(coords)
                if (downloaded == null) {
                    cached
                } else {
                    cacher.save(coords, downloaded)
                    downloaded
                }
            } else {
                cached
            }
        }?.convertTo(units)

    private fun shouldUpdate(timestamp: Instant, updatePolicy: UpdatePolicy): Boolean =
        when (updatePolicy) {
            UpdatePolicy.Static -> false
            UpdatePolicy.Force -> true
            else -> Duration.between(
                timestamp,
                Instant.now()
            ) >= Duration.ofHours(if (updatePolicy == UpdatePolicy.Eager) 1 else 6)
        }
}

enum class UpdatePolicy {
    Eager, Frugal, Static, Force
}