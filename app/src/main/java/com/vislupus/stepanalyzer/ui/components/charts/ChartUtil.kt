package com.vislupus.stepanalyzer.ui.components.charts

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One drawable series: x values (seconds) and y values of the same length. */
class ChartSeries(val x: DoubleArray, val y: DoubleArray, val color: Color, val strokeWidthFactor: Float = 1f, val label: String = "")

/** Point markers (e.g. detected steps). */
class ChartMarkers(val x: DoubleArray, val y: DoubleArray, val color: Color, val label: String = "")

object ChartUtil {

    /**
     * Min/max bucket downsampling of the samples inside [xMin, xMax]: every bucket keeps its
     * lowest and highest sample so peaks survive. Returns index pairs into the arrays.
     */
    fun visibleIndices(x: DoubleArray, y: DoubleArray, xMin: Double, xMax: Double, maxPoints: Int): IntArray {
        if (x.isEmpty()) return IntArray(0)
        var lo = lowerBound(x, xMin)
        var hi = upperBound(x, xMax)
        if (lo > 0) lo--
        if (hi < x.size) hi++
        val n = hi - lo
        if (n <= 0) return IntArray(0)
        if (n <= maxPoints) return IntArray(n) { lo + it }
        val buckets = maxPoints / 2
        val size = ceil(n / buckets.toDouble()).toInt()
        val out = ArrayList<Int>(2 * buckets + 2)
        var i = lo
        while (i < hi) {
            val end = minOf(hi, i + size)
            var iMin = i
            var iMax = i
            for (k in i until end) {
                if (y[k] < y[iMin]) iMin = k
                if (y[k] > y[iMax]) iMax = k
            }
            if (iMin < iMax) { out.add(iMin); out.add(iMax) } else if (iMin > iMax) { out.add(iMax); out.add(iMin) } else out.add(iMin)
            i = end
        }
        return out.toIntArray()
    }

    /** First index with x[i] >= v. */
    fun lowerBound(x: DoubleArray, v: Double): Int {
        var lo = 0
        var hi = x.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (x[mid] < v) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** First index with x[i] > v. */
    fun upperBound(x: DoubleArray, v: Double): Int {
        var lo = 0
        var hi = x.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (x[mid] <= v) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** "Nice" tick spacing (1, 2, 5 x 10^k) giving roughly `target` ticks across the range. */
    fun niceStep(range: Double, target: Int): Double {
        if (range <= 0 || target <= 0) return 1.0
        val raw = range / target
        val mag = 10.0.pow(floor(log10(raw)))
        val norm = raw / mag
        val nice = when {
            norm < 1.5 -> 1.0
            norm < 3.5 -> 2.0
            norm < 7.5 -> 5.0
            else -> 10.0
        }
        return nice * mag
    }

    fun ticks(min: Double, max: Double, target: Int): List<Double> {
        val step = niceStep(max - min, target)
        val first = ceil(min / step) * step
        val out = ArrayList<Double>()
        var v = first
        var guard = 0
        while (v <= max + step * 1e-6 && guard < 100) {
            out.add(if (abs(v) < step * 1e-9) 0.0 else v)
            v += step
            guard++
        }
        return out
    }

    fun formatTick(v: Double, step: Double): String {
        val decimals = when {
            step >= 1 -> 0
            step >= 0.1 -> 1
            else -> 2
        }
        return String.format(java.util.Locale.US, "%.${decimals}f", v)
    }

    /** Seconds -> m:ss for time axes. */
    fun formatTime(seconds: Double): String {
        val s = seconds.toInt().coerceAtLeast(0)
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }
}
