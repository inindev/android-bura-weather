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
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Re-arms the refresh alarm after a reboot. Alarms do not survive a reboot, and the framework does
 * not reliably call onUpdate on boot, so without this the [WidgetAlarmScheduler] chain would stay
 * dead until the next add/app-update. Only re-arms when a widget instance actually exists.
 */
class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) WidgetAlarmScheduler.schedule(context)
    }
}
