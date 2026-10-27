package com.vislupus.stepanalyzer.core.signal

import kotlin.math.ceil
import kotlin.math.max

/** Resampling helpers with numpy semantics. */
object Interp {

    /**
     * Linear interpolation like `numpy.interp(grid, xp, fp)`. `xp` must be increasing;
     * values outside the range are clamped to the edge samples.
     */
    fun interp(grid: DoubleArray, xp: DoubleArray, fp: DoubleArray): DoubleArray {
        val n = xp.size
        val out = DoubleArray(grid.size)
        if (n == 0) return out
        var j = 0
        for (i in grid.indices) {
            val g = grid[i]
            if (g <= xp[0]) { out[i] = fp[0]; continue }
            if (g >= xp[n - 1]) { out[i] = fp[n - 1]; continue }
            // grid is increasing in our use, so a forward-moving cursor is enough,
            // but fall back to a binary search if it is not.
            if (j >= n - 1 || !(xp[j] <= g && g < xp[j + 1])) {
                var lo = 0
                var hi = n - 1
                while (hi - lo > 1) {
                    val mid = (lo + hi) ushr 1
                    if (xp[mid] <= g) lo = mid else hi = mid
                }
                j = lo
            }
            val x0 = xp[j]
            val x1 = xp[j + 1]
            val t = if (x1 == x0) 0.0 else (g - x0) / (x1 - x0)
            out[i] = fp[j] + t * (fp[j + 1] - fp[j])
        }
        return out
    }

    /** `numpy.arange(0, stop, step)`: ceil(stop / step) points at i * step. */
    fun arange(stop: Double, step: Double): DoubleArray {
        val n = max(0, ceil(stop / step).toInt())
        return DoubleArray(n) { it * step }
    }

    /** `numpy.linspace(start, stop, num)`. */
    fun linspace(start: Double, stop: Double, num: Int): DoubleArray {
        if (num <= 1) return DoubleArray(num) { start }
        val step = (stop - start) / (num - 1)
        return DoubleArray(num) { start + it * step }
    }

    /**
     * Sort by time, drop duplicate timestamps (keeping the first occurrence) and return
     * the cleaned (t, values...) arrays. Mirrors `np.argsort` + `np.unique(return_index)`.
     */
    fun sortUnique(t: DoubleArray, vararg values: DoubleArray): Array<DoubleArray> {
        val order = (0 until t.size).sortedBy { t[it] }
        val keepIdx = IntList(t.size)
        var last = Double.NaN
        for (idx in order) {
            val v = t[idx]
            if (v != last) {
                keepIdx.add(idx)
                last = v
            }
        }
        val kept = keepIdx.toArray()
        val result = Array(values.size + 1) { DoubleArray(kept.size) }
        for (i in kept.indices) {
            result[0][i] = t[kept[i]]
            for (v in values.indices) result[v + 1][i] = values[v][kept[i]]
        }
        return result
    }
}
