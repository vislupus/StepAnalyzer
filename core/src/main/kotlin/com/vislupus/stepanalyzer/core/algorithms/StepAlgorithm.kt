package com.vislupus.stepanalyzer.core.algorithms

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.core.model.Note
import com.vislupus.stepanalyzer.core.model.ParamSpec
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.model.SensorKind
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.max

/**
 * Contract for a step-counting algorithm. Implementations are stateless objects; every
 * tunable value comes in through `params` (missing keys fall back to the ParamSpec default).
 */
interface StepAlgorithm {
    val id: String
    val family: AlgorithmFamily
    /** Sensor streams the algorithm cannot work without (beyond the accelerometer). */
    val requires: Set<SensorKind> get() = emptySet()
    val paramSpecs: List<ParamSpec>

    fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult

    /** Default parameter map built from the specs. */
    fun defaults(): Map<String, Double> = paramSpecs.associate { it.key to it.default }

    /** Resolve one parameter: explicit value, else the spec default. */
    fun param(params: Map<String, Double>, spec: ParamSpec): Double = params[spec.key] ?: spec.default

    /** Which streams are missing from the preprocessed data. */
    fun missing(pre: Preprocessed): List<SensorKind> = requires.filter { !pre.streams.containsKey(it) }

    /** A result marking the algorithm as unavailable for this recording. */
    fun unavailable(params: Map<String, Double>, missing: List<SensorKind>): AlgorithmResult = AlgorithmResult(
        id = id,
        family = family,
        available = false,
        stepCount = 0,
        stepIndices = IntArray(0),
        cadenceSpm = 0.0,
        params = paramSpecs.associate { it.key to param(params, it) },
        notes = missing.map { Note.RequiresSensor(it) },
    )
}

/** Helpers shared by the algorithm implementations. */
object AlgorithmSupport {

    /** Steps per minute over the active (moving) duration. */
    fun cadence(stepCount: Int, activeS: Double): Double =
        if (activeS <= 0) 0.0 else stepCount / activeS * 60.0

    /** Seconds of signal whose local std exceeds the floor, i.e. where the phone was moving. */
    fun activeDuration(
        signal: DoubleArray,
        fs: Double,
        winS: Double = CoreDefaults.Activity.WINDOW_S,
        stdFloor: Double = CoreDefaults.Activity.STD_FLOOR,
    ): Double {
        val win = max(1, (winS * fs).toInt())
        val n = signal.size
        var active = 0
        var start = 0
        while (start < n) {
            val end = minOf(n, start + win)
            if (end > start && Stats.std(signal, start, end) >= stdFloor) active += end - start
            start += win
        }
        return if (fs > 0) active / fs else 0.0
    }

    /** Coefficient of variation of inter-step intervals (lower = steadier). NaN if undefined. */
    fun intervalCv(stepIndices: IntArray, fs: Double): Double {
        if (stepIndices.size < 3) return Double.NaN
        val dts = ArrayList<Double>(stepIndices.size)
        for (i in 1 until stepIndices.size) {
            val dt = (stepIndices[i] - stepIndices[i - 1]) / fs
            if (dt > CoreDefaults.Activity.INTERVAL_MIN_S && dt < CoreDefaults.Activity.INTERVAL_MAX_S) dts.add(dt)
        }
        if (dts.size < 2) return Double.NaN
        val arr = dts.toDoubleArray()
        val m = Stats.mean(arr)
        if (m == 0.0) return Double.NaN
        return Stats.std(arr) / m
    }

    /** Number of hop positions the windowed algorithms iterate over. */
    fun windowStarts(n: Int, win: Int, hop: Int): IntArray {
        val limit = max(1, n - win + 1)
        val count = (limit + hop - 1) / hop
        return IntArray(count) { it * hop }
    }

    /**
     * Steps implied by the final partial region that the hop loop skips, using the last
     * non-zero window frequency (port of `_tail_correction`).
     */
    fun tailCorrection(lastFreqHz: Double, n: Int, fs: Double, win: Int, hop: Int, windowsCount: Int): Double {
        val infl = minOf(hop, win) / fs
        val covered = windowsCount * infl
        val tail = max(0.0, (n / fs) - covered - (win - minOf(hop, win)) / fs)
        return if (tail > 0 && lastFreqHz > 0) lastFreqHz * tail else 0.0
    }
}
