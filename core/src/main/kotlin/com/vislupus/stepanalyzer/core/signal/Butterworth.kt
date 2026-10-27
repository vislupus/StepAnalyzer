package com.vislupus.stepanalyzer.core.signal

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** Transfer-function coefficients of a digital IIR filter (numerator b, denominator a). */
class FilterCoefficients(val b: DoubleArray, val a: DoubleArray)

/**
 * Digital Butterworth filter design.
 *
 * This is a faithful port of `scipy.signal.butter(order, [low, high], btype="band")`:
 * analog prototype -> low-pass to band-pass transform -> bilinear transform -> polynomial
 * coefficients. Frequencies are normalised to the Nyquist frequency (0..1).
 */
object Butterworth {

    /**
     * Band-pass design.
     * @param order filter order of the prototype (the band-pass filter has 2*order poles)
     * @param low lower cut-off, normalised to Nyquist (0 < low < high < 1)
     * @param high upper cut-off, normalised to Nyquist
     */
    fun bandpass(order: Int, low: Double, high: Double): FilterCoefficients {
        require(order >= 1) { "order must be >= 1" }
        require(low > 0.0 && low < high && high < 1.0) { "need 0 < low < high < 1 (got $low, $high)" }

        // scipy uses fs = 2 so that normalised frequencies map onto [0, 1].
        val fs = 2.0
        val w1 = 2.0 * fs * tan(PI * low / fs)
        val w2 = 2.0 * fs * tan(PI * high / fs)

        // --- analog Butterworth prototype (buttap) ---
        val poles = ArrayList<Complex>(order)
        var m = -order + 1
        while (m < order) {
            val theta = PI * m / (2.0 * order)
            poles.add(-Complex.polar(1.0, theta))
            m += 2
        }
        var k = 1.0

        // --- low-pass -> band-pass (lp2bp_zpk) ---
        val bw = w2 - w1
        val wo = sqrt(w1 * w2)
        val degree = poles.size // prototype has no finite zeros
        val woSq = Complex(wo * wo, 0.0)
        val pLp = poles.map { it * (bw / 2.0) }
        val pBp = ArrayList<Complex>(2 * degree)
        for (p in pLp) pBp.add(p + (p * p - woSq).sqrt())
        for (p in pLp) pBp.add(p - (p * p - woSq).sqrt())
        val zBp = ArrayList<Complex>(degree)
        repeat(degree) { zBp.add(Complex.ZERO) }
        k *= bw.pow(degree)

        // --- bilinear transform (bilinear_zpk) ---
        val fs2 = Complex(2.0 * fs, 0.0)
        val relDegree = pBp.size - zBp.size
        val zZ = zBp.map { (fs2 + it) / (fs2 - it) }.toMutableList()
        val pZ = pBp.map { (fs2 + it) / (fs2 - it) }
        var num = Complex.ONE
        for (z in zBp) num *= (fs2 - z)
        var den = Complex.ONE
        for (p in pBp) den *= (fs2 - p)
        k *= (num / den).re
        repeat(relDegree) { zZ.add(Complex(-1.0, 0.0)) }

        // --- zpk -> ba ---
        val b = poly(zZ).map { it.re * k }.toDoubleArray()
        val a = poly(pZ).map { it.re }.toDoubleArray()
        return FilterCoefficients(b, a)
    }

    /** Expand prod_i (x - r_i) into polynomial coefficients, highest power first. */
    private fun poly(roots: List<Complex>): List<Complex> {
        var c: MutableList<Complex> = mutableListOf(Complex.ONE)
        for (r in roots) {
            val n = MutableList(c.size + 1) { Complex.ZERO }
            for (i in c.indices) {
                n[i] = n[i] + c[i]
                n[i + 1] = n[i + 1] - c[i] * r
            }
            c = n
        }
        return c
    }
}
