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

package com.davidtakac.bura.forecast.download

import com.davidtakac.bura.forecast.Forecast
import com.davidtakac.bura.places.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

class ForecastDownloader(private val userAgent: String) {
    suspend fun get(coords: Coordinates): Forecast? =
        downloadForecastJson(coords)?.let {
            convertDownloadJsonToForecast(it)
        }

    private suspend fun downloadForecastJson(coords: Coordinates): JSONObject? =
        withContext(Dispatchers.IO) {
            val url = URL(openMeteoUrl(coords))
            val conn = try {
                url.openConnection() as HttpsURLConnection
            } catch (_: Exception) {
                return@withContext null
            }

            // HttpsURLConnection's connect/read are uninterruptible blocking calls, so a coroutine
            // timeout (e.g. the widget's refresh deadline) cannot cancel them on its own. Closing the
            // connection when the job is cancelled unblocks the blocked thread promptly instead of
            // letting it run to its full connect/read timeout. (Note: name resolution happens before a
            // socket exists and is not covered by connectTimeout or by this — that path still falls
            // back to the OS resolver's own timeout.)
            val cancelHandle = coroutineContext.job.invokeOnCompletion {
                runCatching { conn.disconnect() }
            }

            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.setRequestProperty("User-Agent", userAgent)
                if (conn.responseCode != 200) return@withContext null
                val jsonString =
                    BufferedReader(InputStreamReader(conn.inputStream)).use(BufferedReader::readText)
                JSONObject(jsonString)
            } catch (_: Exception) {
                null
            } finally {
                cancelHandle.dispose()
                conn.disconnect()
            }
        }

    private fun openMeteoUrl(coords: Coordinates): String =
        "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${formatCoordinate(coords.latitude)}" +
                "&longitude=${formatCoordinate(coords.longitude)}" +
                "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,precipitation_probability,rain,showers,snowfall,weather_code,pressure_msl,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day" +
                "&daily=sunrise,sunset" +
                "&wind_speed_unit=ms" +
                // timezone=auto returns whole days for the desired location
                "&timezone=auto" +
                "&past_days=1"

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value)

    private companion object {
        // Kept short so a single failed attempt resolves well inside the widget's refresh deadline
        // (see WeatherWidgetProvider.REFRESH_DEADLINE_MS) rather than holding a spinner for tens of
        // seconds. Open-Meteo responds in well under a second on a healthy connection.
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 6_000
    }
}