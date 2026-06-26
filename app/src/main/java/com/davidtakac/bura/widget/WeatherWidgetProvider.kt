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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.davidtakac.bura.R
import com.davidtakac.bura.forecast.UpdatePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Broadcast entry point for the widget. Data work (network included) runs off the broadcast thread
 * via [runAsync] (goAsync plus a coroutine) so it cannot block long enough to ANR. The periodic and
 * on-wake refresh cadence is owned by the framework through updatePeriodMillis (see
 * res/xml/weather_widget_info.xml).
 *
 * Updates intentionally avoid WorkManager. Enqueuing a one-time job toggles WorkManager's
 * RescheduleReceiver component, and on some platforms that component change is broadcast as
 * PACKAGE_CHANGED, which the AppWidget framework surfaces as another onUpdate — re-enqueuing work
 * and spinning a tight update loop. Running the fetch directly via goAsync avoids that side effect.
 */
class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Fired by the framework on the updatePeriodMillis cadence — including the deferred tick
        // delivered when the device wakes from Doze — as well as on widget add and app update.
        runAsync { WidgetUpdater.updateAll(context, UpdatePolicy.Eager) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_PLACE -> withWidgetId(intent) { id ->
                runAsync {
                    WidgetUpdater.update(
                        context, AppWidgetManager.getInstance(context),
                        id, advancePlace = true, updatePolicy = UpdatePolicy.Eager
                    )
                }
            }

            ACTION_REFRESH -> withWidgetId(intent) { id ->
                showSpinner(context, id)
                runAsync {
                    // Hold the spinner briefly so it animates at least once.
                    delay(MIN_SPIN_MS)
                    WidgetUpdater.update(
                        context, AppWidgetManager.getInstance(context),
                        id, advancePlace = false, updatePolicy = UpdatePolicy.Force
                    )
                }
            }

            // Taps on non-interactive regions land here (via the transparent catcher); ignored.
            ACTION_NOOP -> Unit
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetPlaceStore(context)
        for (appWidgetId in appWidgetIds) store.remove(appWidgetId)
    }

    /**
     * Runs [block] off the broadcast thread while keeping the broadcast alive via goAsync(), then
     * releases it. Capped by [ASYNC_TIMEOUT_MS] so a slow network can never hold the broadcast past
     * the ANR window. A failed fetch leaves the current content in place; the next tick retries.
     */
    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(ASYNC_TIMEOUT_MS) { block() }
            } catch (_: Throwable) {
                // Swallow: don't crash the broadcast on a fetch/timeout failure.
            } finally {
                pending.finish()
            }
        }
    }

    /** Flip the ↻ to the spinner immediately (cheap, main-thread safe); the refresh reverts it. */
    private fun showSpinner(context: Context, appWidgetId: Int) {
        val busy = RemoteViews(context.packageName, R.layout.widget_weather).apply {
            setViewVisibility(R.id.widget_refresh, View.GONE)
            setViewVisibility(R.id.widget_refresh_progress, View.VISIBLE)
        }
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, busy)
    }

    private inline fun withWidgetId(intent: Intent, block: (Int) -> Unit) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) block(id)
    }

    companion object {
        const val ACTION_TOGGLE_PLACE = "com.davidtakac.bura.widget.ACTION_TOGGLE_PLACE"
        const val ACTION_REFRESH = "com.davidtakac.bura.widget.ACTION_REFRESH"
        const val ACTION_NOOP = "com.davidtakac.bura.widget.ACTION_NOOP"

        // Hold the manual-refresh spinner long enough to animate at least once.
        private const val MIN_SPIN_MS = 800L

        // Cap the async fetch so the broadcast always finishes well inside the ANR window.
        private const val ASYNC_TIMEOUT_MS = 9_000L
    }
}