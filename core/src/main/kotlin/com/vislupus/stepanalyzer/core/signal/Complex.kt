package com.vislupus.stepanalyzer.core.signal

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Minimal immutable complex number used by the filter designer and the FFT. */
internal data class Complex(val re: Double, val im: Double) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(k: Double) = Complex(re * k, im * k)
    operator fun unaryMinus() = Complex(-re, -im)

    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    fun abs(): Double = hypot(re, im)

    fun conj() = Complex(re, -im)

    /** Principal square root (same branch as numpy.sqrt for complex input). */
    fun sqrt(): Complex {
        val r = abs()
        val a = kotlin.math.sqrt(max(0.0, (r + re) / 2.0))
        val b = kotlin.math.sqrt(max(0.0, (r - re) / 2.0))
        return Complex(a, if (im < 0) -b else b)
    }

    companion object {
        val ZERO = Complex(0.0, 0.0)
        val ONE = Complex(1.0, 0.0)
        fun polar(r: Double, theta: Double) = Complex(r * cos(theta), r * sin(theta))
    }
}
