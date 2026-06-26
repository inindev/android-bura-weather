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
import com.davidtakac.bura.R
import com.davidtakac.bura.forecast.parameters.humidity.Humidity
import com.davidtakac.bura.forecast.parameters.pressure.Pressure
import com.davidtakac.bura.forecast.parameters.temperature.Temperature
import com.davidtakac.bura.forecast.parameters.temperature.string
import com.davidtakac.bura.forecast.parameters.uvindex.UvIndex
import com.davidtakac.bura.forecast.parameters.wind.Wind
import com.davidtakac.bura.forecast.parameters.wind.WindSpeed
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * Non-Composable formatting for the widget. Mirrors the app's unit handling but works
 * outside of Compose (the app's own `string()` helpers are `@Composable`).
 */

internal fun Temperature.widgetString(context: Context, nf: NumberFormat): String =
    string(context, nf)

internal fun highLowString(
    context: Context,
    nf: NumberFormat,
    high: Temperature,
    low: Temperature
): String = context.getString(
    R.string.widget_high_low,
    high.widgetString(context, nf),
    low.widgetString(context, nf)
)

internal fun Humidity.widgetString(context: Context, nf: NumberFormat): String =
    context.getString(R.string.humidity_value_percent, nf.format(round(value, 0)))

internal fun UvIndex.widgetString(nf: NumberFormat): String =
    // Rounded up to match the app's UV index display.
    nf.format(BigDecimal.valueOf(value).setScale(0, RoundingMode.CEILING))

internal fun Pressure.widgetString(context: Context, nf: NumberFormat): String {
    val scale = if (unit == Pressure.Unit.InchesOfMercury) 2 else 0
    val valueStr = nf.format(round(value, scale))
    val resId = when (unit) {
        Pressure.Unit.Hectopascal -> R.string.pressure_value_hpa
        Pressure.Unit.InchesOfMercury -> R.string.pressure_value_inhg
        Pressure.Unit.MillimetersOfMercury -> R.string.pressure_value_mmhg
    }
    return context.getString(resId, valueStr)
}

internal fun Wind.widgetString(context: Context, nf: NumberFormat): String {
    val scale = if (speed.unit == WindSpeed.Unit.MetersPerSecond) 1 else 0
    val valueStr = nf.format(round(speed.value, scale))
    val resId = when (speed.unit) {
        WindSpeed.Unit.MetersPerSecond -> R.string.wind_value_mps
        WindSpeed.Unit.KilometersPerHour -> R.string.wind_value_kph
        WindSpeed.Unit.MilesPerHour -> R.string.wind_value_mph
        WindSpeed.Unit.Knots -> R.string.wind_value_kn
    }
    return "${context.getString(resId, valueStr)} ${from.compass.name}"
}

private fun round(value: Double, scale: Int): BigDecimal =
    BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP)