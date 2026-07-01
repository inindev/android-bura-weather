/*
 * Copyright 2026 John Clark
 *
 * This file is part of Bura.
 *
 * Bura is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Bura is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Bura. If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidtakac.bura.widget

import android.content.Context
import com.davidtakac.bura.forecast.ForecastRepository
import com.davidtakac.bura.forecast.ForecastResult
import com.davidtakac.bura.forecast.UpdatePolicy
import com.davidtakac.bura.forecast.parameters.condition.image
import com.davidtakac.bura.forecast.parameters.condition.string
import com.davidtakac.bura.forecast.units.SelectedUnitsRepository
import com.davidtakac.bura.places.saved.SavedPlacesRepository
import com.davidtakac.bura.places.selected.SelectedPlaceRepository
import com.davidtakac.bura.summary.daily.getDailySummary
import com.davidtakac.bura.summary.now.getNowSummary
import com.davidtakac.bura.theme.AppIcons
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Builds a [WidgetState] for a widget by reusing the app's existing data layer. Each widget
 * resolves its own saved place, falling back to the app-wide selected place and then the first
 * saved place (see the resolution in [invoke]).
 */
class GetWidgetState(
    private val selectedPlaceRepo: SelectedPlaceRepository,
    private val savedPlacesRepo: SavedPlacesRepository,
    private val selectedUnitsRepo: SelectedUnitsRepository,
    private val forecastRepo: ForecastRepository,
    private val widgetPlaceStore: WidgetPlaceStore
) {
    suspend operator fun invoke(
        context: Context,
        now: Instant,
        appWidgetId: Int,
        updatePolicy: UpdatePolicy = UpdatePolicy.Eager
    ): WidgetState {
        val savedPlaces = savedPlacesRepo.getSavedPlaces()
        if (savedPlaces.isEmpty()) return WidgetState.NoPlace

        // Resolve this widget's place: its stored choice, else the app-wide selection (if
        // still saved), else the first saved place. Persist the fallback so it sticks.
        val storedId = widgetPlaceStore.getCoordsId(appWidgetId)
        val place = savedPlaces.firstOrNull { it.location.coordinates.id == storedId }
            ?: run {
                val selected = selectedPlaceRepo.getSelectedPlace()
                val resolved = savedPlaces.firstOrNull {
                    selected != null && it.location.coordinates == selected.location.coordinates
                } ?: savedPlaces.first()
                widgetPlaceStore.setCoordsId(appWidgetId, resolved.location.coordinates.id)
                resolved
            }

        val units = selectedUnitsRepo.getSelectedUnits()
        val coords = place.location.coordinates
        val result = forecastRepo.getResult(coords, units, updatePolicy)
        val forecast = when (result) {
            is ForecastResult.Fresh -> result.forecast
            is ForecastResult.Stale -> result.forecast
            ForecastResult.None -> return WidgetState.NoData
        }
        val stale = result is ForecastResult.Stale

        val zone = place.location.timeZone
        val nowDateTime = now.atZone(zone).toLocalDateTime()
        val nf = NumberFormat.getInstance(Locale.getDefault())
        val icons = AppIcons.ForDarkTheme

        val nowSummary = getNowSummary(
            now = nowDateTime,
            tempPeriod = forecast.temperature,
            feelsPeriod = forecast.feelsLike,
            condPeriod = forecast.condition
        ) ?: return WidgetState.NoData
        val daily = getDailySummary(
            now = nowDateTime,
            tempPeriod = forecast.temperature,
            condPeriod = forecast.condition,
            popPeriod = forecast.pop
        ) ?: return WidgetState.NoData

        val humidity = forecast.humidity[nowDateTime]?.humidity
        val uvIndex = forecast.uvIndex[nowDateTime]?.uvIndex
        val pressure = forecast.pressure[nowDateTime]?.pressure
        val wind = forecast.wind[nowDateTime]?.wind

        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy - EEE", Locale.getDefault())
        val updatedFormatter = DateTimeFormatter.ofPattern("MMM d  h:mm a", Locale.getDefault())

        return WidgetState.Loaded(
            location = place.name,
            coordsId = coords.id,
            timeZoneId = zone.id,
            todayHighLow = highLowString(context, nf, nowSummary.maxTemp, nowSummary.minTemp),
            date = nowDateTime.format(dateFormatter),
            currentIcon = nowSummary.cond.image(context, icons),
            temperature = nowSummary.temp.widgetString(context, nf),
            feelsLike = context.getString(
                com.davidtakac.bura.R.string.widget_feels_like,
                nowSummary.feelsLike.widgetString(context, nf)
            ),
            condition = nowSummary.cond.string(context),
            humidity = context.getString(
                com.davidtakac.bura.R.string.widget_humidity,
                humidity?.widgetString(context, nf) ?: "—"
            ),
            uvIndex = context.getString(
                com.davidtakac.bura.R.string.widget_uv_index,
                uvIndex?.widgetString(nf) ?: "—"
            ),
            pressure = context.getString(
                com.davidtakac.bura.R.string.widget_pressure,
                pressure?.widgetString(context, nf) ?: "—"
            ),
            wind = context.getString(
                com.davidtakac.bura.R.string.widget_wind,
                wind?.widgetString(context, nf) ?: "—"
            ),
            updated = forecast.timestamp.atZone(zone).format(updatedFormatter),
            stale = stale,
            days = daily.days.take(5).map { day ->
                WidgetState.Loaded.Day(
                    label = day.time.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .uppercase(Locale.getDefault()),
                    date = day.time,
                    icon = day.desc.image(context, icons),
                    highLow = highLowString(context, nf, day.max, day.min)
                )
            }
        )
    }
}