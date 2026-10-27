package com.vislupus.stepanalyzer.core.signal

import kotlin.math.ceil
import kotlin.math.max

/**
 * Peak finding ported from `scipy.signal.find_peaks` (local maxima with plateau handling,
 * minimum distance selection by peak height priority and prominence filtering).
 */
object Peaks {

    /** Indices of all local maxima; plateaus are reported at their midpoint. */
    fun localMaxima(x: DoubleArray): IntArray {
        val out = IntList()
        val iMax = x.size - 1
        var i = 1
        while (i < iMax) {
            if (x[i - 1] < x[i]) {
                var iAhead = i + 1
                while (iAhead < iMax && x[iAhead] == x[i]) iAhead++
                if (x[iAhead] < x[i]) {
                    out.add((i + iAhead - 1) / 2)
                    i = iAhead
                }
            }
            i++
        }
        return out.toArray()
    }

    /** Topographic prominence of each peak (no window limit, like wlen=None). */
    fun prominences(x: DoubleArray, peaks: IntArray): DoubleArray {
        val n = x.size
        return DoubleArray(peaks.size) { k ->
            val p = peaks[k]
            val h = x[p]
            var i = p
            var leftMin = h
            while (i >= 0 && x[i] <= h) {
                if (x[i] < leftMin) leftMin = x[i]
                i--
            }
            i = p
            var rightMin = h
            while (i < n && x[i] <= h) {
                if (x[i] < rightMin) rightMin = x[i]
                i++
            }
            h - max(leftMin, rightMin)
        }
    }

    /** Keep peaks that are at least `distance` samples apart, preferring higher peaks. */
    fun selectByDistance(peaks: IntArray, priority: DoubleArray, distance: Double): BooleanArray {
        val size = peaks.size
        val dist = ceil(distance).toInt()
        val keep = BooleanArray(size) { true }
        val order = (0 until size).sortedBy { priority[it] }
        for (idx in size - 1 downTo 0) {
            val j = order[idx]
            if (!keep[j]) continue
            var k = j - 1
            while (k >= 0 && peaks[j] - peaks[k] < dist) {
                keep[k] = false
                k--
            }
            k = j + 1
            while (k < size && peaks[k] - peaks[j] < dist) {
                keep[k] = false
                k++
            }
        }
        return keep
    }

    /**
     * Equivalent of `scipy.signal.find_peaks(x, height=, distance=, prominence=)`.
     * Conditions are applied in the same order as scipy: height, distance, prominence.
     */
    fun findPeaks(
        x: DoubleArray,
        height: Double? = null,
        distance: Int? = null,
        prominence: Double? = null,
    ): IntArray {
        var peaks = localMaxima(x)
        if (height != null) peaks = peaks.filter { x[it] >= height }.toIntArray()
        if (distance != null && peaks.size > 1) {
            val keep = selectByDistance(peaks, DoubleArray(peaks.size) { x[peaks[it]] }, distance.toDouble())
            peaks = peaks.filterIndexed { i, _ -> keep[i] }.toIntArray()
        }
        if (prominence != null && peaks.isNotEmpty()) {
            val prom = prominences(x, peaks)
            peaks = peaks.filterIndexed { i, _ -> prom[i] >= prominence }.toIntArray()
        }
        return peaks
    }
}

/** Growable primitive int list (avoids boxing in hot loops). */
class IntList(initialCapacity: Int = 64) {
    private var data = IntArray(max(4, initialCapacity))
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Int = data[i]
    fun last(): Int = data[size - 1]
    fun isEmpty() = size == 0
    fun toArray(): IntArray = data.copyOf(size)
}

/** Growable primitive double list. */
class DoubleList(initialCapacity: Int = 64) {
    private var data = DoubleArray(max(4, initialCapacity))
    var size = 0
        private set

    fun add(v: Double) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Double = data[i]
    fun isEmpty() = size == 0
    fun toArray(): DoubleArray = data.copyOf(size)
}
