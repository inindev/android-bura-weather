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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.view.View
import android.widget.RemoteViews
import com.davidtakac.bura.MainActivity
import com.davidtakac.bura.R

/** Pure mapping from [WidgetState] to a [RemoteViews] for the widget layout. */
object WidgetRenderer {
    private val dayLabelIds = intArrayOf(
        R.id.widget_day1_label, R.id.widget_day2_label, R.id.widget_day3_label,
        R.id.widget_day4_label, R.id.widget_day5_label
    )
    private val dayIconIds = intArrayOf(
        R.id.widget_day1_icon, R.id.widget_day2_icon, R.id.widget_day3_icon,
        R.id.widget_day4_icon, R.id.widget_day5_icon
    )
    private val dayHighLowIds = intArrayOf(
        R.id.widget_day1_high_low, R.id.widget_day2_high_low, R.id.widget_day3_high_low,
        R.id.widget_day4_high_low, R.id.widget_day5_high_low
    )
    private val dayContainerIds = intArrayOf(
        R.id.widget_day1, R.id.widget_day2, R.id.widget_day3,
        R.id.widget_day4, R.id.widget_day5
    )

    fun render(context: Context, state: WidgetState, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        // Transparent catcher behind the content consumes taps on non-interactive regions so
        // they never fall through to the launcher's "open owning app" fallback. Interactive
        // views sit above it and win by z-order.
        views.setOnClickPendingIntent(R.id.widget_background_catcher, noopIntent(context, appWidgetId))
        when (state) {
            WidgetState.NoPlace -> {
                views.setTextViewText(R.id.widget_location, context.getString(R.string.widget_no_place))
                // Tap opens the app so the user can add/select a place.
                views.setOnClickPendingIntent(R.id.widget_location, openAppIntent(context))
            }

            WidgetState.NoData ->
                views.setTextViewText(R.id.widget_location, context.getString(R.string.widget_no_data))

            is WidgetState.Loaded -> {
                bindLoaded(views, state)
                // Tap the location name to cycle to the next saved place.
                views.setOnClickPendingIntent(R.id.widget_location, togglePlaceIntent(context, appWidgetId))
                // Clock -> system alarm app; date -> calendar.
                views.setOnClickPendingIntent(R.id.widget_clock, clockIntent(context, appWidgetId))
                views.setOnClickPendingIntent(R.id.widget_clock_ampm, clockIntent(context, appWidgetId))
                views.setOnClickPendingIntent(R.id.widget_date, calendarIntent(context, appWidgetId))
                // Weather icon -> the app's Summary overview (today + 5 days) for this place.
                views.setOnClickPendingIntent(
                    R.id.widget_current_icon,
                    summaryIntent(context, appWidgetId, state.coordsId)
                )
                // Each forecast day -> that day's graphs.
                state.days.forEachIndexed { i, day ->
                    views.setOnClickPendingIntent(
                        dayContainerIds[i],
                        graphsIntent(
                            context, appWidgetId, state.coordsId,
                            route = "essential-graphs?initialDay=${day.date}",
                            key = "day/${day.date}",
                            slot = 6 + i
                        )
                    )
                }
            }
        }
        // Resting state: static ↻ shown, spinner hidden (a running refresh swaps these).
        views.setViewVisibility(R.id.widget_refresh, View.VISIBLE)
        views.setViewVisibility(R.id.widget_refresh_progress, View.GONE)
        // ↻ forces a refresh regardless of state; wired to the enlarged corner overlay.
        views.setOnClickPendingIntent(R.id.widget_refresh_hotspot, refreshIntent(context, appWidgetId))
        return views
    }

    // Distinct request code per click target so PendingIntents never alias one another.
    private fun requestCode(appWidgetId: Int, slot: Int) = appWidgetId * 100 + slot

    /** Consumes taps on non-interactive regions so they don't open the app; intentionally a no-op. */
    private fun noopIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_NOOP
            data = Uri.parse("bura://widget/$appWidgetId/noop")
        }
        return PendingIntent.getBroadcast(
            context, requestCode(appWidgetId, 0), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refreshIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("bura://widget/$appWidgetId/refresh")
        }
        return PendingIntent.getBroadcast(
            context, requestCode(appWidgetId, 11), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun togglePlaceIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
            action = WeatherWidgetProvider.ACTION_TOGGLE_PLACE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse("bura://widget/$appWidgetId/toggle")
        }
        return PendingIntent.getBroadcast(
            context, requestCode(appWidgetId, 1), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clockIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, requestCode(appWidgetId, 2), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calendarIntent(context: Context, appWidgetId: Int): PendingIntent {
        // CATEGORY_APP_CALENDAR opens the default calendar app to today without touching the
        // calendar content provider, which some OEMs (e.g. Samsung) gate behind READ_CALENDAR.
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, requestCode(appWidgetId, 4), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Opens the app's Summary overview (today + 5 days) for [coordsId]; no deep-link route. */
    private fun summaryIntent(context: Context, appWidgetId: Int, coordsId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_PLACE_ID, coordsId)
            data = Uri.parse("bura://widget/$appWidgetId/summary")
        }
        return PendingIntent.getActivity(
            context, requestCode(appWidgetId, 5), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Opens the app to the graphs screen for [coordsId], optionally at a specific day (via [route]). */
    private fun graphsIntent(
        context: Context,
        appWidgetId: Int,
        coordsId: String,
        route: String,
        key: String,
        slot: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_PLACE_ID, coordsId)
            putExtra(MainActivity.EXTRA_NAV_ROUTE, route)
            data = Uri.parse("bura://widget/$appWidgetId/$key")
        }
        return PendingIntent.getActivity(
            context, requestCode(appWidgetId, slot), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun bindLoaded(views: RemoteViews, state: WidgetState.Loaded) {
        views.setTextViewText(R.id.widget_location, state.location)
        views.setTextViewText(R.id.widget_today_high_low, state.todayHighLow)
        views.setTextViewText(R.id.widget_date, state.date)
        // Alarm line is not yet implemented; keep it hidden.
        views.setViewVisibility(R.id.widget_alarm, View.GONE)

        // Clock self-ticks; pin it to the displayed place's time zone.
        views.setString(R.id.widget_clock, "setTimeZone", state.timeZoneId)
        views.setString(R.id.widget_clock_ampm, "setTimeZone", state.timeZoneId)

        views.setImageViewResource(R.id.widget_current_icon, state.currentIcon)
        views.setTextViewText(R.id.widget_temperature, state.temperature)
        views.setTextViewText(R.id.widget_feels_like, state.feelsLike)
        views.setTextViewText(R.id.widget_condition, state.condition)
        views.setTextViewText(R.id.widget_humidity, state.humidity)
        views.setTextViewText(R.id.widget_uv, state.uvIndex)
        views.setTextViewText(R.id.widget_pressure, state.pressure)
        views.setTextViewText(R.id.widget_wind, state.wind)
        views.setTextViewText(R.id.widget_updated, state.updated)

        for (i in dayLabelIds.indices) {
            val day = state.days.getOrNull(i)
            if (day == null) {
                views.setViewVisibility(dayLabelIds[i], View.INVISIBLE)
                views.setViewVisibility(dayIconIds[i], View.INVISIBLE)
                views.setViewVisibility(dayHighLowIds[i], View.INVISIBLE)
            } else {
                views.setViewVisibility(dayLabelIds[i], View.VISIBLE)
                views.setViewVisibility(dayIconIds[i], View.VISIBLE)
                views.setViewVisibility(dayHighLowIds[i], View.VISIBLE)
                views.setTextViewText(dayLabelIds[i], day.label)
                views.setImageViewResource(dayIconIds[i], day.icon)
                views.setTextViewText(dayHighLowIds[i], day.highLow)
            }
        }
    }
}