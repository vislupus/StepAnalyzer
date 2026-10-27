package com.vislupus.stepanalyzer.core.signal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Discrete Fourier transform for arbitrary lengths. Powers of two use an iterative
 * radix-2 algorithm; other lengths use Bluestein's chirp-z algorithm, so the result matches
 * `numpy.fft.rfft` without zero padding (which would change the frequency grid).
 */
object Fft {

    /** In-place radix-2 FFT. `re.size` must be a power of two. */
    fun fftPow2(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        require(n and (n - 1) == 0) { "length must be a power of two" }
        // bit reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len * (if (inverse) 1.0 else -1.0)
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                    val vIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }

    private fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /** Forward DFT of a complex sequence of any length. Returns (re, im). */
    fun fft(re: DoubleArray, im: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = re.size
        if (n == 0) return Pair(DoubleArray(0), DoubleArray(0))
        if (n and (n - 1) == 0) {
            val r = re.copyOf()
            val i = im.copyOf()
            fftPow2(r, i)
            return Pair(r, i)
        }
        // Bluestein: X_k = w_k * sum_j (x_j w_j) * conj(w_{k-j}),  w_k = exp(-i*pi*k^2/n)
        val m = nextPow2(2 * n - 1)
        val chirpRe = DoubleArray(n)
        val chirpIm = DoubleArray(n)
        for (k in 0 until n) {
            // k^2 mod 2n keeps the angle small and exact
            val kk = (k.toLong() * k) % (2L * n)
            val ang = -PI * kk / n
            chirpRe[k] = cos(ang)
            chirpIm[k] = sin(ang)
        }
        val aRe = DoubleArray(m)
        val aIm = DoubleArray(m)
        for (k in 0 until n) {
            aRe[k] = re[k] * chirpRe[k] - im[k] * chirpIm[k]
            aIm[k] = re[k] * chirpIm[k] + im[k] * chirpRe[k]
        }
        val bRe = DoubleArray(m)
        val bIm = DoubleArray(m)
        bRe[0] = chirpRe[0]
        bIm[0] = -chirpIm[0]
        for (k in 1 until n) {
            bRe[k] = chirpRe[k]; bIm[k] = -chirpIm[k]
            bRe[m - k] = chirpRe[k]; bIm[m - k] = -chirpIm[k]
        }
        fftPow2(aRe, aIm)
        fftPow2(bRe, bIm)
        for (k in 0 until m) {
            val r = aRe[k] * bRe[k] - aIm[k] * bIm[k]
            val i = aRe[k] * bIm[k] + aIm[k] * bRe[k]
            aRe[k] = r
            aIm[k] = i
        }
        fftPow2(aRe, aIm, inverse = true)
        val outRe = DoubleArray(n)
        val outIm = DoubleArray(n)
        for (k in 0 until n) {
            outRe[k] = aRe[k] * chirpRe[k] - aIm[k] * chirpIm[k]
            outIm[k] = aRe[k] * chirpIm[k] + aIm[k] * chirpRe[k]
        }
        return Pair(outRe, outIm)
    }

    /** |rfft(x)| for bins 0..n/2 (same as `numpy.abs(numpy.fft.rfft(x))`). */
    fun rfftMagnitude(x: DoubleArray): DoubleArray {
        val n = x.size
        val (re, im) = fft(x, DoubleArray(n))
        val bins = n / 2 + 1
        return DoubleArray(bins) { hypot(re[it], im[it]) }
    }

    /** Frequency of each rfft bin for sample rate fs (`numpy.fft.rfftfreq`). */
    fun rfftFreq(n: Int, fs: Double): DoubleArray {
        val bins = n / 2 + 1
        return DoubleArray(bins) { it * fs / n }
    }

    /** Symmetric Hann window (`numpy.hanning`). */
    fun hanning(n: Int): DoubleArray {
        if (n == 1) return doubleArrayOf(1.0)
        return DoubleArray(n) { 0.5 - 0.5 * cos(2.0 * PI * it / (n - 1)) }
    }
}
