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

package com.davidtakac.bura.forecast.cache

import com.davidtakac.bura.common.util.getStringOrNull
import com.davidtakac.bura.forecast.Forecast
import com.davidtakac.bura.places.Coordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ForecastCacher(
    private val root: File,
    private val appVersionName: String,
) {
    // Read and written from the main thread (view models) and IO threads (widget broadcasts).
    private val coordsToData = ConcurrentHashMap<Coordinates, Forecast>()

    suspend fun get(coords: Coordinates): Forecast? {
        coordsToData[coords]?.let { return it }

        val file = findForecastFile(coords) ?: return null
        val fromFile = fileToForecast(file)
        if (fromFile == null) {
            // Corrupt or incompatible cache file. Delete it so it can't fail every future
            // read -- before this, one torn file made all updates for the place throw forever.
            withContext(Dispatchers.IO) { file.delete() }
            return null
        }
        coordsToData[coords] = fromFile

        return fromFile
    }

    suspend fun save(coords: Coordinates, data: Forecast) {
        val jsonString = forecastToJsonString(data)
        val dir = getDir()
        withContext(Dispatchers.IO) {
            // Write-then-rename so the cache file is replaced atomically: a plain writeText
            // leaves torn JSON behind if the process dies (or another writer interleaves)
            // mid-write.
            val tmp = File(dir, "${coords.id}.tmp")
            tmp.writeText(jsonString)
            tmp.renameTo(File(dir, coords.id))
        }
        coordsToData[coords] = data
    }

    suspend fun delete(coords: Coordinates) {
        val file = findForecastFile(coords) ?: return
        withContext(Dispatchers.IO) {
            file.delete()
        }
        coordsToData.remove(coords)
    }

    private suspend fun findForecastFile(coords: Coordinates): File? =
        withContext(Dispatchers.IO) {
            getDir().listFiles()
        }?.firstOrNull { it.name == coords.id }

    /** Null when the file is unparseable (torn write, schema drift), not just version-mismatched. */
    private suspend fun fileToForecast(file: File): Forecast? = try {
        val json = JSONObject(
            withContext(Dispatchers.IO) {
                file.readText()
            }
        )
        if (json.getStringOrNull(CacheJsonSerialNames.APP_VERSION_NAME) == null) {
            null
        } else {
            convertCacheJsonToForecast(json)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun forecastToJsonString(data: Forecast): String =
        withContext(Dispatchers.Default) {
            convertForecastToCacheJson(data).apply {
                put(CacheJsonSerialNames.APP_VERSION_NAME, appVersionName)
            }.toString()
        }

    private suspend fun getDir(): File =
        withContext(Dispatchers.IO) {
            File(root, "forecasts").apply {
                mkdir()
            }
        }
}