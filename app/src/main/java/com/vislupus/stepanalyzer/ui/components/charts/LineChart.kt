package com.vislupus.stepanalyzer.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.ui.theme.extra

/**
 * Time-series chart with grid, axes and optional markers. `xRange` selects the visible window
 * (seconds); the data is downsampled per draw so very long recordings stay smooth.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    height: Dp = AppConfig.Ui.signalChartHeight,
    xRange: ClosedFloatingPointRange<Double>? = null,
    markers: ChartMarkers? = null,
    yLabel: String? = null,
    yRangeOverride: ClosedFloatingPointRange<Double>? = null,
    stepped: Boolean = false,
    fillUnderFirst: Boolean = false,
    referenceY: Double? = null,
    maxPoints: Int = AppConfig.Analysis.CHART_MAX_POINTS,
) {
    val gridColor = MaterialTheme.extra.chartGrid
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = textColor)
    val density = LocalDensity.current
    val lineWidth = with(density) { AppConfig.Ui.chartLineWidth.toPx() }
    val markerRadius = with(density) { AppConfig.Ui.chartMarkerRadius.toPx() }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val padLeft = 40.dp.toPx()
        val padRight = 8.dp.toPx()
        val padTop = 10.dp.toPx()
        val padBottom = 20.dp.toPx()
        val w = size.width - padLeft - padRight
        val h = size.height - padTop - padBottom
        if (w <= 0 || h <= 0) return@Canvas

        // visible x range
        var xMin = xRange?.start ?: series.filter { it.x.isNotEmpty() }.minOfOrNull { it.x.first() } ?: 0.0
        var xMax = xRange?.endInclusive ?: series.filter { it.x.isNotEmpty() }.maxOfOrNull { it.x.last() } ?: 1.0
        if (xMax <= xMin) xMax = xMin + 1.0

        // downsample and compute y range
        val visible = series.map { s -> ChartUtil.visibleIndices(s.x, s.y, xMin, xMax, maxPoints) }
        var yMin = Double.POSITIVE_INFINITY
        var yMax = Double.NEGATIVE_INFINITY
        if (yRangeOverride != null) {
            yMin = yRangeOverride.start
            yMax = yRangeOverride.endInclusive
        } else {
            series.forEachIndexed { i, s -> for (k in visible[i]) { val v = s.y[k]; if (v < yMin) yMin = v; if (v > yMax) yMax = v } }
            referenceY?.let { if (it < yMin) yMin = it; if (it > yMax) yMax = it }
            if (yMin == Double.POSITIVE_INFINITY) { yMin = -1.0; yMax = 1.0 }
            if (yMax - yMin < 1e-9) { yMin -= 1.0; yMax += 1.0 }
            val padY = (yMax - yMin) * 0.08
            yMin -= padY
            yMax += padY
        }
        if (yMax <= yMin) { yMax = yMin + 1.0 }

        fun px(x: Double) = (padLeft + (x - xMin) / (xMax - xMin) * w).toFloat()
        fun py(y: Double) = (padTop + (1 - (y - yMin) / (yMax - yMin)) * h).toFloat()

        // grid + y labels
        val yTicks = ChartUtil.ticks(yMin, yMax, 4)
        val yStep = ChartUtil.niceStep(yMax - yMin, 4)
        for (t in yTicks) {
            val y = py(t)
            drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + w, y), strokeWidth = 1f)
            val text = ChartUtil.formatTick(t, yStep)
            val layout = textMeasurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(padLeft - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }
        // x labels
        val xTicks = ChartUtil.ticks(xMin, xMax, 5)
        for (t in xTicks) {
            val x = px(t)
            drawLine(gridColor, Offset(x, padTop), Offset(x, padTop + h), strokeWidth = 1f)
            val layout = textMeasurer.measure(ChartUtil.formatTime(t), labelStyle)
            val tx = (x - layout.size.width / 2f).coerceIn(padLeft, padLeft + w - layout.size.width)
            drawText(layout, topLeft = Offset(tx, padTop + h + 4.dp.toPx()))
        }
        // zero line
        if (yMin < 0 && yMax > 0) {
            drawLine(gridColor.copy(alpha = 1f), Offset(padLeft, py(0.0)), Offset(padLeft + w, py(0.0)), strokeWidth = 1.5f)
        }
        referenceY?.let {
            drawLine(textColor, Offset(padLeft, py(it)), Offset(padLeft + w, py(it)), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }

        // series
        series.forEachIndexed { si, s ->
            val idx = visible[si]
            if (idx.isEmpty()) return@forEachIndexed
            val path = Path()
            var first = true
            var lastX = 0f
            var lastY = 0f
            for (k in idx) {
                val x = px(s.x[k])
                val y = py(s.y[k])
                if (first) { path.moveTo(x, y); first = false } else {
                    if (stepped) path.lineTo(x, lastY)
                    path.lineTo(x, y)
                }
                lastX = x
                lastY = y
            }
            if (fillUnderFirst && si == 0) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(lastX, py(yMin))
                    lineTo(px(s.x[idx[0]]), py(yMin))
                    close()
                }
                drawPath(fill, s.color.copy(alpha = 0.12f))
            }
            drawPath(path, s.color, style = Stroke(width = lineWidth * s.strokeWidthFactor))
        }

        // markers
        markers?.let { m ->
            val lo = ChartUtil.lowerBound(m.x, xMin)
            val hi = ChartUtil.upperBound(m.x, xMax)
            if (hi - lo <= 600) {
                for (k in lo until hi) drawCircle(m.color, markerRadius, Offset(px(m.x[k]), py(m.y[k])))
            } else {
                // too dense to draw circles: ticks along the top
                for (k in lo until hi) {
                    val x = px(m.x[k])
                    drawLine(m.color, Offset(x, padTop), Offset(x, padTop + 6.dp.toPx()), strokeWidth = 1f)
                }
            }
        }

        yLabel?.let {
            val layout = textMeasurer.measure(it, labelStyle)
            drawText(layout, topLeft = Offset(padLeft + 4.dp.toPx(), padTop))
        }
        // frame
        drawRect(gridColor, Offset(padLeft, padTop), androidx.compose.ui.geometry.Size(w, h), style = Stroke(1f))
    }
}

/** Convenience for drawing a label in a DrawScope at a given position. */
internal fun DrawScope.labelAt(text: String, x: Float, y: Float, style: TextStyle, measurer: androidx.compose.ui.text.TextMeasurer) {
    drawText(measurer.measure(text, style), topLeft = Offset(x, y))
}

/** Rolling chart for the live recording signal (fixed window, latest sample at the right). */
@Composable
fun LiveChart(
    samples: FloatArray,
    capacity: Int,
    modifier: Modifier = Modifier,
    height: Dp = AppConfig.Ui.liveChartHeight,
    color: Color = MaterialTheme.colorScheme.primary,
    threshold: Float? = null,
) {
    val gridColor = MaterialTheme.extra.chartGrid
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val lineWidth = with(density) { AppConfig.Ui.chartLineWidth.toPx() }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        var yMax = 2.0f
        for (v in samples) if (kotlin.math.abs(v) > yMax) yMax = kotlin.math.abs(v)
        yMax *= 1.1f
        fun py(v: Float) = h / 2f - v / yMax * (h / 2f)
        drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
        threshold?.let {
            drawLine(textColor.copy(alpha = 0.5f), Offset(0f, py(it)), Offset(w, py(it)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawLine(textColor.copy(alpha = 0.5f), Offset(0f, py(-it)), Offset(w, py(-it)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        if (samples.size < 2) return@Canvas
        val step = w / (capacity - 1).coerceAtLeast(1)
        val offset = (capacity - samples.size) * step
        val path = Path()
        for (i in samples.indices) {
            val x = offset + i * step
            val y = py(samples[i])
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(lineWidth))
    }
}
