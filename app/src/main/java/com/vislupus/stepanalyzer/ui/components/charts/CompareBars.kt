package com.vislupus.stepanalyzer.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vislupus.stepanalyzer.config.AppConfig

/** One row of the comparison chart. */
data class CompareBar(val label: String, val value: Double, val color: Color, val highlight: Boolean = false, val dimmed: Boolean = false)

/**
 * Horizontal bar chart comparing step counts of all algorithms, with optional dashed
 * reference lines (phone step counter, GPX-implied count, ...).
 */
@Composable
fun CompareBars(
    bars: List<CompareBar>,
    modifier: Modifier = Modifier,
    references: List<Pair<String, Double>> = emptyList(),
) {
    val maxValue = (bars.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(references.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0) * 1.05
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val refColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        for (bar in bars) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bar.dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (bar.highlight) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                Spacer(Modifier.width(8.dp))
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(AppConfig.Ui.compareBarHeight),
                ) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(trackColor, Offset.Zero, size, r)
                    val w = (bar.value / maxValue * size.width).toFloat().coerceIn(0f, size.width)
                    if (w > 0) drawRoundRect(bar.color.copy(alpha = if (bar.dimmed) 0.35f else 1f), Offset.Zero, Size(w, size.height), r)
                    for ((_, v) in references) {
                        val x = (v / maxValue * size.width).toFloat()
                        drawLine(refColor, Offset(x, -2f), Offset(x, size.height + 2f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (bar.value > 0) bar.value.toInt().toString() else "–",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (bar.highlight) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
        if (references.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                references.joinToString("   ") { "- - ${it.first}: ${it.second.toInt()}" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
