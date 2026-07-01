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

package com.davidtakac.bura.graphs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.davidtakac.bura.R
import com.davidtakac.bura.common.util.capitalize
import com.davidtakac.bura.forecast.parameters.condition.image
import com.davidtakac.bura.forecast.parameters.temperature.string
import com.davidtakac.bura.graphs.temperature.TemperatureGraphSummary
import com.davidtakac.bura.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the first five days of [summaries] to a PNG (day label, condition icon, high/low) and hands
 * it to the system share sheet as an image, so it can be sent straight into a messaging app instead of
 * screenshotting and cropping. Rendering and file IO run off the main thread; the chooser is launched
 * on return.
 */
suspend fun shareForecastImage(context: Context, summaries: List<TemperatureGraphSummary>) {
    val uri = withContext(Dispatchers.IO) {
        val bitmap = renderForecastBitmap(context, summaries)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "forecast.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private const val COLUMN_WIDTH = 220
private const val CARD_HEIGHT = 320
private const val PADDING = 32
private const val ICON_SIZE = 120
private const val BACKGROUND = 0xFF1C1B2E.toInt()

private fun renderForecastBitmap(context: Context, summaries: List<TemperatureGraphSummary>): Bitmap {
    val days = summaries.take(5)
    val locale = Locale.getDefault()
    val numberFormat = NumberFormat.getInstance(locale)
    val dayFormatter = DateTimeFormatter.ofPattern(context.getString(R.string.date_time_pattern_dow), locale)
    val icons = AppIcons.ForDarkTheme

    val width = PADDING * 2 + COLUMN_WIDTH * days.size.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(BACKGROUND)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 44f
    }

    days.forEachIndexed { index, summary ->
        val centerX = PADDING + COLUMN_WIDTH * index + COLUMN_WIDTH / 2
        val dayOfWeek = dayFormatter.format(summary.day).capitalize(locale)
        val dayOfMonth = numberFormat.format(summary.day.dayOfMonth)
        canvas.drawText("$dayOfWeek $dayOfMonth", centerX.toFloat(), 76f, labelPaint)

        ContextCompat.getDrawable(context, summary.condition.image(context, icons))?.apply {
            setBounds(centerX - ICON_SIZE / 2, 104, centerX + ICON_SIZE / 2, 104 + ICON_SIZE)
            draw(canvas)
        }

        val high = summary.maxTemp.string(context, numberFormat)
        val low = summary.minTemp.string(context, numberFormat)
        canvas.drawText("$high/$low", centerX.toFloat(), 284f, tempPaint)
    }
    return bitmap
}