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
import androidx.core.content.edit

/**
 * Per-widget-instance selected place, keyed by [appWidgetId]. Independent of the app's
 * app-wide selected place, so toggling one widget never affects the app or other widgets.
 * Stores the place's `Coordinates.id`.
 */
class WidgetPlaceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("widget_places", Context.MODE_PRIVATE)

    fun getCoordsId(appWidgetId: Int): String? = prefs.getString(key(appWidgetId), null)

    fun setCoordsId(appWidgetId: Int, coordsId: String) =
        prefs.edit { putString(key(appWidgetId), coordsId) }

    fun remove(appWidgetId: Int) = prefs.edit { remove(key(appWidgetId)) }

    private fun key(appWidgetId: Int) = "place_$appWidgetId"
}