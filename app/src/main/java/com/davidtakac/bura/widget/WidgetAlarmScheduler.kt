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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Drives the widget's periodic refresh via a self-rescheduling one-shot alarm rather than the
 * framework's updatePeriodMillis. The framework's periodic tick is an inexact repeating alarm that
 * App Standby and Doze defer heavily on modern (esp. OEM) builds — often indefinitely — so the
 * widget never refreshes on its own. This chain fires a single [WeatherWidgetProvider.ACTION_TICK]
 * broadcast, and the provider re-arms the next one when it handles the tick.
 *
 * The alarm is [AlarmManager.setExactAndAllowWhileIdle] on RTC_WAKEUP. Exact + allow-while-idle so
 * it holds the 30-minute cadence in Doze instead of drifting hours late (a plain inexact tick is
 * deferred to Doze maintenance windows, which grow further apart the longer the device stays idle —
 * the source of the stale-by-hours widget). RTC_WAKEUP so the tick actually fires while the screen
 * is off, rather than waiting for the user to wake the device and then showing stale data. The
 * platform still rate-limits an app's allow-while-idle wakeups to about one every 9-10 minutes in
 * Doze, comfortably under our interval. Needs USE_EXACT_ALARM (auto-granted at install on our
 * minSdk 35; see AndroidManifest).
 *
 * A one-shot chain, not [AlarmManager.setRepeating]: repeating alarms cannot be exact or
 * allow-while-idle, so a plain repeating alarm would be deferred by Doze exactly like
 * updatePeriodMillis already is.
 */
object WidgetAlarmScheduler {
    // Matches updatePeriodMillis (the retained framework backstop). 30 min is a sensible widget
    // cadence and comfortably above the allow-while-idle Doze rate limit.
    private const val INTERVAL_MS = 30 * 60 * 1000L

    // Stable request code so schedule/cancel address the same PendingIntent across process deaths.
    private const val REQUEST_CODE = 1

    /** Arms (or re-arms, replacing any pending one) the next tick, [INTERVAL_MS] from now. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + INTERVAL_MS,
            pendingIntent(context)
        )
    }

    /** Cancels the pending tick. Called when the last widget instance is removed. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WeatherWidgetProvider::class.java)
            .setAction(WeatherWidgetProvider.ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
