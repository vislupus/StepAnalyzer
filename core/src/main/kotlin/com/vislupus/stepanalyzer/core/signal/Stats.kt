package com.vislupus.stepanalyzer.core.signal

import kotlin.math.floor
import kotlin.math.sqrt

/** Basic descriptive statistics with numpy semantics (population std, linear percentile). */
object Stats {
    fun mean(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        val n = to - from
        if (n <= 0) return Double.NaN
        var s = 0.0
        for (i in from until to) s += x[i]
        return s / n
    }

    /** Population standard deviation (ddof = 0), like `numpy.std`. */
    fun std(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        val n = to - from
        if (n <= 0) return Double.NaN
        val m = mean(x, from, to)
        var s = 0.0
        for (i in from until to) {
            val d = x[i] - m
            s += d * d
        }
        return sqrt(s / n)
    }

    fun median(x: DoubleArray): Double {
        if (x.isEmpty()) return Double.NaN
        val s = x.copyOf()
        s.sort()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else 0.5 * (s[n / 2 - 1] + s[n / 2])
    }

    /** Linear-interpolated percentile (numpy default), q in 0..100. */
    fun percentile(x: DoubleArray, q: Double): Double {
        if (x.isEmpty()) return Double.NaN
        val s = x.copyOf()
        s.sort()
        val pos = (q / 100.0) * (s.size - 1)
        val lo = floor(pos).toInt()
        val hi = minOf(lo + 1, s.size - 1)
        val frac = pos - lo
        return s[lo] + (s[hi] - s[lo]) * frac
    }

    fun max(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var m = Double.NEGATIVE_INFINITY
        for (i in from until to) if (x[i] > m) m = x[i]
        return m
    }

    fun min(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var m = Double.POSITIVE_INFINITY
        for (i in from until to) if (x[i] < m) m = x[i]
        return m
    }

    fun argmax(x: DoubleArray, from: Int = 0, to: Int = x.size): Int {
        var best = from
        for (i in from + 1 until to) if (x[i] > x[best]) best = i
        return best
    }

    fun dot(x: DoubleArray, y: DoubleArray): Double {
        var s = 0.0
        for (i in x.indices) s += x[i] * y[i]
        return s
    }

    fun meanAbs(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        val n = to - from
        if (n <= 0) return Double.NaN
        var s = 0.0
        for (i in from until to) s += kotlin.math.abs(x[i])
        return s / n
    }

    /** Subtract the mean from every sample. */
    fun centred(x: DoubleArray): DoubleArray {
        val m = mean(x)
        return DoubleArray(x.size) { x[it] - m }
    }

    /**
     * Normalised autocorrelation for lags 0..maxLag computed directly (O(n * maxLag)).
     * ac[0] == 1 when the signal has non-zero energy.
     */
    fun autocorrelation(x: DoubleArray, maxLag: Int): DoubleArray {
        val n = x.size
        val lags = minOf(maxLag, n - 1)
        val out = DoubleArray(lags + 1)
        if (n == 0) return out
        val denom = dot(x, x)
        if (denom <= 0.0) return out
        for (k in 0..lags) {
            var s = 0.0
            for (i in 0 until n - k) s += x[i] * x[i + k]
            out[k] = s / denom
        }
        return out
    }
}
