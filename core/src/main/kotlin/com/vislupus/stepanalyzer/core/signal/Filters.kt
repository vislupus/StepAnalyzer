package com.vislupus.stepanalyzer.core.signal

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * IIR filtering utilities ported from scipy.signal: lfilter, lfilter_zi, filtfilt (with the
 * default odd-extension padding), plus a linear detrend and a moving average.
 */
object Filters {

    /** Direct-form II transposed IIR filter, identical to `scipy.signal.lfilter`. */
    fun lfilter(b: DoubleArray, a: DoubleArray, x: DoubleArray, zi: DoubleArray? = null): DoubleArray {
        val n = max(a.size, b.size)
        val bb = DoubleArray(n)
        val aa = DoubleArray(n)
        for (i in b.indices) bb[i] = b[i] / a[0]
        for (i in a.indices) aa[i] = a[i] / a[0]
        val z = DoubleArray(max(1, n - 1))
        if (zi != null) System.arraycopy(zi, 0, z, 0, min(zi.size, z.size))
        val y = DoubleArray(x.size)
        if (n == 1) {
            for (i in x.indices) y[i] = bb[0] * x[i]
            return y
        }
        for (i in x.indices) {
            val xi = x[i]
            val yi = bb[0] * xi + z[0]
            for (j in 0 until n - 2) z[j] = bb[j + 1] * xi + z[j + 1] - aa[j + 1] * yi
            z[n - 2] = bb[n - 1] * xi - aa[n - 1] * yi
            y[i] = yi
        }
        return y
    }

    /** Steady-state initial conditions for a step input (`scipy.signal.lfilter_zi`). */
    fun lfilterZi(b: DoubleArray, a: DoubleArray): DoubleArray {
        val n = max(a.size, b.size)
        val bb = DoubleArray(n)
        val aa = DoubleArray(n)
        for (i in b.indices) bb[i] = b[i] / a[0]
        for (i in a.indices) aa[i] = a[i] / a[0]
        val m = n - 1
        if (m == 0) return DoubleArray(0)
        // IminusA = I - companion(a).T
        val mat = Array(m) { DoubleArray(m) }
        for (i in 0 until m) {
            for (j in 0 until m) {
                var cT = 0.0
                if (j == 0) cT += -aa[i + 1]
                if (j == i + 1) cT += 1.0
                mat[i][j] = (if (i == j) 1.0 else 0.0) - cT
            }
        }
        val rhs = DoubleArray(m) { bb[it + 1] - aa[it + 1] * bb[0] }
        return LinearAlgebra.solve(mat, rhs)
    }

    /**
     * Zero-phase forward-backward filtering (`scipy.signal.filtfilt` with padtype="odd" and
     * padlen = 3 * max(len(a), len(b))).
     */
    fun filtfilt(coeffs: FilterCoefficients, x: DoubleArray): DoubleArray {
        val b = coeffs.b
        val a = coeffs.a
        val n = x.size
        if (n == 0) return DoubleArray(0)
        var edge = 3 * max(a.size, b.size)
        if (n <= edge) edge = n - 1
        if (edge < 0) edge = 0

        val ext = DoubleArray(n + 2 * edge)
        for (i in 0 until edge) ext[i] = 2 * x[0] - x[edge - i]
        System.arraycopy(x, 0, ext, edge, n)
        for (i in 0 until edge) ext[edge + n + i] = 2 * x[n - 1] - x[n - 2 - i]

        val zi = lfilterZi(b, a)
        val x0 = ext[0]
        val fwd = lfilter(b, a, ext, DoubleArray(zi.size) { zi[it] * x0 })
        val y0 = fwd[fwd.size - 1]
        fwd.reverse()
        val bwd = lfilter(b, a, fwd, DoubleArray(zi.size) { zi[it] * y0 })
        bwd.reverse()
        return bwd.copyOfRange(edge, edge + n)
    }

    /** Remove the least-squares straight line from the signal (`scipy.signal.detrend`). */
    fun detrendLinear(x: DoubleArray): DoubleArray {
        val n = x.size
        if (n < 2) return x.copyOf()
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) {
            val xi = i.toDouble()
            sx += xi; sy += x[i]; sxx += xi * xi; sxy += xi * x[i]
        }
        val denom = n * sxx - sx * sx
        val slope = if (abs(denom) < 1e-12) 0.0 else (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n
        return DoubleArray(n) { x[it] - (slope * it + intercept) }
    }

    /** Causal moving average over `window` samples (shorter at the start). */
    fun movingAverage(x: DoubleArray, window: Int): DoubleArray {
        val w = max(1, window)
        val out = DoubleArray(x.size)
        var sum = 0.0
        for (i in x.indices) {
            sum += x[i]
            if (i >= w) sum -= x[i - w]
            out[i] = sum / min(i + 1, w)
        }
        return out
    }

    /** Centred moving average (same length, edges use the available samples). */
    fun centredMovingAverage(x: DoubleArray, window: Int): DoubleArray {
        val w = max(1, window)
        val half = w / 2
        val n = x.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + x[i]
        return DoubleArray(n) { i ->
            val lo = max(0, i - half)
            val hi = min(n, i + half + 1)
            (prefix[hi] - prefix[lo]) / (hi - lo)
        }
    }

    /** Single-pole exponential moving average low-pass with the given cut-off frequency. */
    fun emaLowPass(x: DoubleArray, fs: Double, cutoffHz: Double): DoubleArray {
        val rc = 1.0 / (2.0 * Math.PI * max(1e-6, cutoffHz))
        val dt = 1.0 / fs
        val alpha = dt / (rc + dt)
        val out = DoubleArray(x.size)
        if (x.isEmpty()) return out
        out[0] = x[0]
        for (i in 1 until x.size) out[i] = out[i - 1] + alpha * (x[i] - out[i - 1])
        return out
    }
}

/** Tiny dense linear-algebra helper (Gaussian elimination with partial pivoting). */
internal object LinearAlgebra {
    fun solve(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val n = rhs.size
        val a = Array(n) { matrix[it].copyOf() }
        val b = rhs.copyOf()
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (pivot != col) {
                val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
                val tb = b[col]; b[col] = b[pivot]; b[pivot] = tb
            }
            val p = a[col][col]
            if (abs(p) < 1e-300) continue
            for (r in col + 1 until n) {
                val f = a[r][col] / p
                if (f == 0.0) continue
                for (c in col until n) a[r][c] -= f * a[col][c]
                b[r] -= f * b[col]
            }
        }
        val x = DoubleArray(n)
        for (r in n - 1 downTo 0) {
            var s = b[r]
            for (c in r + 1 until n) s -= a[r][c] * x[c]
            val p = a[r][r]
            x[r] = if (abs(p) < 1e-300) 0.0 else s / p
        }
        return x
    }
}
