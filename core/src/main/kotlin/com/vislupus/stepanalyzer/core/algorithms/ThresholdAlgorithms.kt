package com.vislupus.stepanalyzer.core.algorithms

import com.vislupus.stepanalyzer.core.analysis.Preprocessor
import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.core.model.ParamSpec
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.signal.Filters
import com.vislupus.stepanalyzer.core.signal.IntList
import com.vislupus.stepanalyzer.core.signal.Peaks
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.max

/**
 * Algorithm F - dynamic threshold crossing, the classic hardware-pedometer design
 * (Zhao, "Full-Featured Pedometer Design", Analog Dialogue 2010).
 *
 * The lightly smoothed dynamic acceleration is split into fixed windows; for every window the
 * threshold is the midpoint between its maximum and minimum and it is applied to the NEXT
 * window. A step is a downward crossing of that threshold, provided the window swing exceeds
 * `min_amplitude` and the previous step is at least `min_interval_s` ago.
 */
object DynamicThresholdAlgorithm : StepAlgorithm {
    const val ID = "dynamic_threshold"
    override val id = ID
    override val family = AlgorithmFamily.THRESHOLD
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.DynamicThreshold.SMOOTH_S, CoreDefaults.DynamicThreshold.THRESHOLD_WINDOW_S,
        CoreDefaults.DynamicThreshold.MIN_AMPLITUDE, CoreDefaults.DynamicThreshold.MIN_INTERVAL_S,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val fs = pre.fs
        val smoothS = param(params, CoreDefaults.DynamicThreshold.SMOOTH_S)
        val thrWindowS = param(params, CoreDefaults.DynamicThreshold.THRESHOLD_WINDOW_S)
        val minAmplitude = param(params, CoreDefaults.DynamicThreshold.MIN_AMPLITUDE)
        val minInterval = param(params, CoreDefaults.DynamicThreshold.MIN_INTERVAL_S)

        val smoothN = max(1, (smoothS * fs).toInt())
        val s = if (smoothN > 1) Filters.movingAverage(pre.aDyn, smoothN) else pre.aDyn
        val win = max(2, (thrWindowS * fs).toInt())
        val minInt = max(1, (minInterval * fs).toInt())
        val n = s.size

        val steps = IntList()
        var threshold = Double.NaN
        var active = false
        var last = Int.MIN_VALUE / 2
        var start = 0
        while (start < n) {
            val end = minOf(n, start + win)
            // detect crossings in this window using the threshold from the previous window
            if (active && !threshold.isNaN()) {
                for (i in max(1, start) until end) {
                    if (s[i - 1] > threshold && s[i] <= threshold && i - last >= minInt) {
                        steps.add(i)
                        last = i
                    }
                }
            }
            val mx = Stats.max(s, start, end)
            val mn = Stats.min(s, start, end)
            active = (mx - mn) >= minAmplitude
            threshold = 0.5 * (mx + mn)
            start = end
        }
        val idx = steps.toArray()
        val activeS = AlgorithmSupport.activeDuration(pre.aFilt, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = idx.size, stepIndices = idx,
            cadenceSpm = AlgorithmSupport.cadence(idx.size, activeS),
            params = mapOf(
                CoreDefaults.DynamicThreshold.SMOOTH_S.key to smoothS,
                CoreDefaults.DynamicThreshold.THRESHOLD_WINDOW_S.key to thrWindowS,
                CoreDefaults.DynamicThreshold.MIN_AMPLITUDE.key to minAmplitude,
                CoreDefaults.DynamicThreshold.MIN_INTERVAL_S.key to minInterval,
            ),
            signal = s,
        )
    }
}

/**
 * Algorithm G - Pan-Tompkins style energy detector borrowed from ECG beat detection:
 * derivative -> half-wave rectify -> square -> moving-window integration -> square root
 * (amplitude envelope), followed by the original adaptive threshold: running estimates of the signal-peak level (SPKI) and the
 * noise-peak level (NPKI) are updated with `learning_rate`, and a candidate counts as a step
 * when it exceeds NPKI + threshold_fraction * (SPKI - NPKI).
 */
object PanTompkinsAlgorithm : StepAlgorithm {
    const val ID = "pan_tompkins"
    override val id = ID
    override val family = AlgorithmFamily.THRESHOLD
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.PanTompkins.BAND_LOW_HZ, CoreDefaults.PanTompkins.INTEGRATION_WINDOW_S,
        CoreDefaults.PanTompkins.MIN_INTERVAL_S, CoreDefaults.PanTompkins.THRESHOLD_FRACTION,
        CoreDefaults.PanTompkins.LEARNING_RATE, CoreDefaults.PanTompkins.RECTIFY,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val fs = pre.fs
        val bandLow = param(params, CoreDefaults.PanTompkins.BAND_LOW_HZ)
        val integS = param(params, CoreDefaults.PanTompkins.INTEGRATION_WINDOW_S)
        val minInterval = param(params, CoreDefaults.PanTompkins.MIN_INTERVAL_S)
        val fraction = param(params, CoreDefaults.PanTompkins.THRESHOLD_FRACTION)
        val rate = param(params, CoreDefaults.PanTompkins.LEARNING_RATE)
        val rectify = param(params, CoreDefaults.PanTompkins.RECTIFY) >= 0.5

        // Own band-pass starting at the step band: the stride component (half the step
        // frequency) is removed so that every step is a distinct burst of energy.
        val x = Preprocessor.bandpass(pre.aDyn, fs, bandLow, pre.options.bandHighHz, pre.options.filterOrder)
        val n = x.size

        // five-point central derivative; optionally only the rising slope is kept
        // (half-wave rectified) so that every step cycle produces one energy burst, then squared
        val sq = DoubleArray(n)
        for (i in 2 until n - 2) {
            val d = (-x[i - 2] - 2 * x[i - 1] + 2 * x[i + 1] + x[i + 2]) * fs / 8.0
            if (!rectify || d > 0) sq[i] = d * d
        }
        // integrate, then take the square root so the envelope is back in amplitude units;
        // this keeps left/right step asymmetry from being squared away into "noise"
        val integ = Filters.centredMovingAverage(sq, max(1, (integS * fs).toInt()))
        for (i in integ.indices) integ[i] = kotlin.math.sqrt(integ[i])

        val minDist = max(1, (minInterval * fs).toInt())
        val cand = Peaks.findPeaks(integ, distance = minDist)
        val accepted = IntList()
        if (cand.isNotEmpty()) {
            // initialise the levels from the first two seconds of candidates
            val warm = cand.filter { it < 2 * fs }.ifEmpty { listOf(cand[0]) }
            var spki = warm.maxOf { integ[it] }
            var npki = 0.5 * spki
            for (p in cand) {
                val v = integ[p]
                val thr = npki + fraction * (spki - npki)
                if (v > thr && v > 0) {
                    accepted.add(p)
                    spki = rate * v + (1 - rate) * spki
                } else {
                    npki = rate * v + (1 - rate) * npki
                }
            }
        }
        val idx = accepted.toArray()
        val active = AlgorithmSupport.activeDuration(x, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = idx.size, stepIndices = idx,
            cadenceSpm = AlgorithmSupport.cadence(idx.size, active),
            params = mapOf(
                CoreDefaults.PanTompkins.BAND_LOW_HZ.key to bandLow,
                CoreDefaults.PanTompkins.INTEGRATION_WINDOW_S.key to integS,
                CoreDefaults.PanTompkins.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.PanTompkins.THRESHOLD_FRACTION.key to fraction,
                CoreDefaults.PanTompkins.LEARNING_RATE.key to rate,
                CoreDefaults.PanTompkins.RECTIFY.key to (if (rectify) 1.0 else 0.0),
            ),
            signal = integ,
        )
    }
}

/**
 * Algorithm J - Schmitt trigger. The detector arms when the filtered signal drops below -T
 * and fires one step when it then rises above +T, where T = max(floor, factor x local std).
 * Hysteresis makes it immune to small wiggles around zero.
 */
object HysteresisAlgorithm : StepAlgorithm {
    const val ID = "hysteresis"
    override val id = ID
    override val family = AlgorithmFamily.THRESHOLD
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.Hysteresis.THRESHOLD_FACTOR, CoreDefaults.Hysteresis.THRESHOLD_FLOOR,
        CoreDefaults.Hysteresis.MIN_INTERVAL_S, CoreDefaults.Hysteresis.LOCAL_WINDOW_S,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val s = pre.aFilt
        val fs = pre.fs
        val factor = param(params, CoreDefaults.Hysteresis.THRESHOLD_FACTOR)
        val floor = param(params, CoreDefaults.Hysteresis.THRESHOLD_FLOOR)
        val minInterval = param(params, CoreDefaults.Hysteresis.MIN_INTERVAL_S)
        val localS = param(params, CoreDefaults.Hysteresis.LOCAL_WINDOW_S)
        val n = s.size
        val minInt = max(1, (minInterval * fs).toInt())
        val block = max(2, (localS * fs).toInt())

        // local std per block, held constant inside the block
        val thresholds = DoubleArray(n)
        var start = 0
        while (start < n) {
            val end = minOf(n, start + block)
            val thr = max(floor, factor * Stats.std(s, start, end))
            for (i in start until end) thresholds[i] = thr
            start = end
        }
        val steps = IntList()
        var armed = false
        var last = Int.MIN_VALUE / 2
        for (i in 0 until n) {
            val t = thresholds[i]
            if (!armed) {
                if (s[i] < -t) armed = true
            } else if (s[i] > t) {
                if (i - last >= minInt) {
                    steps.add(i)
                    last = i
                }
                armed = false
            }
        }
        val idx = steps.toArray()
        val active = AlgorithmSupport.activeDuration(s, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = idx.size, stepIndices = idx,
            cadenceSpm = AlgorithmSupport.cadence(idx.size, active),
            params = mapOf(
                CoreDefaults.Hysteresis.THRESHOLD_FACTOR.key to factor,
                CoreDefaults.Hysteresis.THRESHOLD_FLOOR.key to floor,
                CoreDefaults.Hysteresis.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.Hysteresis.LOCAL_WINDOW_S.key to localS,
            ),
        )
    }
}
