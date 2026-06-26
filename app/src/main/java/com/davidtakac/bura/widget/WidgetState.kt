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

import androidx.annotation.DrawableRes
import java.time.LocalDate

/** Ready-to-render snapshot for one widget instance. All values are display strings. */
sealed interface WidgetState {
    /** No place is selected yet (user has not added/selected one in the app). */
    data object NoPlace : WidgetState

    /** A place is selected but no forecast is available (no cache and download failed). */
    data object NoData : WidgetState

    data class Loaded(
        val location: String,
        val coordsId: String,
        val timeZoneId: String,
        val todayHighLow: String,
        val date: String,
        @DrawableRes val currentIcon: Int,
        val temperature: String,
        val feelsLike: String,
        val condition: String,
        val humidity: String,
        val uvIndex: String,
        val pressure: String,
        val wind: String,
        val updated: String,
        val days: List<Day>
    ) : WidgetState {
        data class Day(
            val label: String,
            val date: LocalDate,
            @DrawableRes val icon: Int,
            val highLow: String
        )
    }
}