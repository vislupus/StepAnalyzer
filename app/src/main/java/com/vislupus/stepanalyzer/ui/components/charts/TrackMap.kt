package com.vislupus.stepanalyzer.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.core.gpx.Gpx
import com.vislupus.stepanalyzer.ui.theme.extra
import kotlin.math.cos

/**
 * Draws the GPS track as a polyline on a blank canvas (no map tiles: everything stays
 * offline). Longitude is scaled by cos(latitude) so shapes are not distorted, and a scale
 * bar gives the size.
 */
@Composable
fun TrackMap(lat: DoubleArray, lon: DoubleArray, modifier: Modifier = Modifier, height: Dp = AppConfig.Ui.mapHeight) {
    val lineColor = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.extra.success
    val endColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.extra.chartGrid
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = textColor)

    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = minOf(lat.size, lon.size)
        if (n < 2) return@Canvas
        val pad = 16.dp.toPx()
        var latMin = Double.POSITIVE_INFINITY; var latMax = Double.NEGATIVE_INFINITY
        var lonMin = Double.POSITIVE_INFINITY; var lonMax = Double.NEGATIVE_INFINITY
        for (i in 0 until n) {
            if (lat[i] < latMin) latMin = lat[i]; if (lat[i] > latMax) latMax = lat[i]
            if (lon[i] < lonMin) lonMin = lon[i]; if (lon[i] > lonMax) lonMax = lon[i]
        }
        val latMid = (latMin + latMax) / 2
        val kx = cos(Math.toRadians(latMid))
        val spanX = (lonMax - lonMin) * kx
        val spanY = latMax - latMin
        val span = maxOf(spanX, spanY, 1e-6)
        val availW = size.width - 2 * pad
        val availH = size.height - 2 * pad
        val scale = minOf(availW / span, availH / span) // px per degree
        val offX = pad + (availW - spanX * scale) / 2
        val offY = pad + (availH - spanY * scale) / 2
        fun px(i: Int) = (offX + (lon[i] - lonMin) * kx * scale).toFloat()
        fun py(i: Int) = (offY + (latMax - lat[i]) * scale).toFloat()

        // light grid
        val cells = 4
        for (c in 0..cells) {
            val x = pad + availW * c / cells
            val y = pad + availH * c / cells
            drawLine(gridColor, Offset(x, pad), Offset(x, pad + availH), 1f)
            drawLine(gridColor, Offset(pad, y), Offset(pad + availW, y), 1f)
        }

        val path = Path()
        path.moveTo(px(0), py(0))
        for (i in 1 until n) path.lineTo(px(i), py(i))
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(startColor, 5.dp.toPx(), Offset(px(0), py(0)))
        drawCircle(endColor, 5.dp.toPx(), Offset(px(n - 1), py(n - 1)))

        // scale bar: distance of 25% of the width
        val metresPerPx = Gpx.haversine(latMid, lonMin, latMid, lonMin + (availW * 0.25 / scale) / kx) / (availW * 0.25)
        val targetM = ChartUtil.niceStep(metresPerPx * availW * 0.3, 1)
        val barPx = (targetM / metresPerPx).toFloat()
        val y = size.height - pad / 2
        drawLine(textColor, Offset(pad, y), Offset(pad + barPx, y), 2f)
        drawLine(textColor, Offset(pad, y - 4f), Offset(pad, y + 4f), 2f)
        drawLine(textColor, Offset(pad + barPx, y - 4f), Offset(pad + barPx, y + 4f), 2f)
        val label = if (targetM >= 1000) String.format(java.util.Locale.US, "%.1f km", targetM / 1000) else "${targetM.toInt()} m"
        val layout = measurer.measure(label, labelStyle)
        drawText(layout, topLeft = Offset(pad + barPx + 6.dp.toPx(), y - layout.size.height / 2f))
    }
}
