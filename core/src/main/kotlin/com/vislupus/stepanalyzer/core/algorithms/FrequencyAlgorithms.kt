package com.vislupus.stepanalyzer.core.algorithms

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.core.model.Note
import com.vislupus.stepanalyzer.core.model.ParamSpec
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.model.WindowCadence
import com.vislupus.stepanalyzer.core.signal.Fft
import com.vislupus.stepanalyzer.core.signal.Peaks
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Algorithm D - windowed autocorrelation cadence.
 *
 * The strongest periodicity of the magnitude signal is often the STRIDE (two steps), so the
 * search is restricted to the step band [f_min, f_max] to avoid locking onto half the cadence.
 */
object AutocorrelationAlgorithm : StepAlgorithm {
    const val ID = "autocorrelation"
    override val id = ID
    override val family = AlgorithmFamily.FREQUENCY
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.Autocorrelation.WINDOW_S, CoreDefaults.Autocorrelation.HOP_S,
        CoreDefaults.Autocorrelation.F_MIN, CoreDefaults.Autocorrelation.F_MAX,
        CoreDefaults.Autocorrelation.STD_FLOOR,
    )

    /** Strongest autocorrelation peak within [lagMin, lagMax]; returns (lag, height). */
    internal fun bestPeak(ac: DoubleArray, lagMin: Int, lagMax: Int): Pair<Int, Double>? {
        val hi = minOf(lagMax, ac.size - 1)
        if (hi < lagMin) return null
        val band = ac.copyOfRange(lagMin, hi + 1)
        if (band.size < 3) {
            val k = Stats.argmax(band)
            return Pair(lagMin + k, band[k])
        }
        val cand = Peaks.findPeaks(band, height = 0.0)
        if (cand.isEmpty()) {
            val k = Stats.argmax(band)
            return Pair(lagMin + k, band[k])
        }
        var best = cand[0]
        for (c in cand) if (band[c] > band[best]) best = c
        return Pair(lagMin + best, band[best])
    }

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val signal = pre.aFilt
        val fs = pre.fs
        val windowS = param(params, CoreDefaults.Autocorrelation.WINDOW_S)
        val hopS = param(params, CoreDefaults.Autocorrelation.HOP_S)
        var fMin = param(params, CoreDefaults.Autocorrelation.F_MIN)
        var fMax = param(params, CoreDefaults.Autocorrelation.F_MAX)
        val stdFloor = param(params, CoreDefaults.Autocorrelation.STD_FLOOR)
        if (fMin >= fMax) { val t = fMin; fMin = fMax; fMax = t }

        val win = max(8, (windowS * fs).toInt())
        val hop = max(1, (hopS * fs).toInt())
        val lagMin = max(1, (fs / fMax).toInt())
        val lagMax = (fs / fMin).toInt()
        val n = signal.size
        val infl = minOf(hop, win) / fs

        var total = 0.0
        val windows = ArrayList<WindowCadence>()
        var lastFreq = 0.0
        var count = 0
        for (start in AlgorithmSupport.windowStarts(n, win, hop)) {
            if (start + win > n) break
            count++
            val x = signal.copyOfRange(start, start + win)
            if (Stats.std(x) < stdFloor) {
                windows.add(WindowCadence(start / fs, 0.0, 0.0))
                continue
            }
            val xc = Stats.centred(x)
            val ac = Stats.autocorrelation(xc, lagMax)
            val (lag, strength) = bestPeak(ac, lagMin, lagMax) ?: continue
            val fStep = fs / lag
            val steps = fStep * infl
            total += steps
            lastFreq = fStep
            windows.add(WindowCadence(start / fs, fStep, steps, strength))
        }
        total += AlgorithmSupport.tailCorrection(lastFreq, n, fs, win, hop, count)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = total.roundToInt(), stepIndices = IntArray(0),
            cadenceSpm = if (n > 0) total / (n / fs) * 60.0 else 0.0,
            params = mapOf(
                CoreDefaults.Autocorrelation.WINDOW_S.key to windowS,
                CoreDefaults.Autocorrelation.HOP_S.key to hopS,
                CoreDefaults.Autocorrelation.F_MIN.key to fMin,
                CoreDefaults.Autocorrelation.F_MAX.key to fMax,
                CoreDefaults.Autocorrelation.STD_FLOOR.key to stdFloor,
            ),
            windows = windows,
        )
    }
}

/**
 * Algorithm E - windowed FFT cadence with a stride -> step sub-harmonic guard: when the
 * stride band carries much more energy than the step band and twice the stride frequency
 * lands inside the step band, the doubled frequency is used.
 */
object FftCadenceAlgorithm : StepAlgorithm {
    const val ID = "fft_cadence"
    override val id = ID
    override val family = AlgorithmFamily.FREQUENCY
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.FftCadence.WINDOW_S, CoreDefaults.FftCadence.HOP_S,
        CoreDefaults.FftCadence.F_MIN, CoreDefaults.FftCadence.F_MAX,
        CoreDefaults.FftCadence.STD_FLOOR, CoreDefaults.FftCadence.STRIDE_FLOOR,
        CoreDefaults.FftCadence.STRIDE_RATIO,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val signal = pre.aFilt
        val fs = pre.fs
        val windowS = param(params, CoreDefaults.FftCadence.WINDOW_S)
        val hopS = param(params, CoreDefaults.FftCadence.HOP_S)
        var fMin = param(params, CoreDefaults.FftCadence.F_MIN)
        var fMax = param(params, CoreDefaults.FftCadence.F_MAX)
        val stdFloor = param(params, CoreDefaults.FftCadence.STD_FLOOR)
        val strideFloor = param(params, CoreDefaults.FftCadence.STRIDE_FLOOR)
        val strideRatio = param(params, CoreDefaults.FftCadence.STRIDE_RATIO)
        if (fMin >= fMax) { val t = fMin; fMin = fMax; fMax = t }

        val win = max(8, (windowS * fs).toInt())
        val hop = max(1, (hopS * fs).toInt())
        val n = signal.size
        val infl = minOf(hop, win) / fs
        val freqs = Fft.rfftFreq(win, fs)
        val hann = Fft.hanning(win)

        var total = 0.0
        val windows = ArrayList<WindowCadence>()
        var lastFreq = 0.0
        var count = 0
        var strideCorrections = 0
        for (start in AlgorithmSupport.windowStarts(n, win, hop)) {
            if (start + win > n) break
            count++
            val x = signal.copyOfRange(start, start + win)
            if (Stats.std(x) < stdFloor) {
                windows.add(WindowCadence(start / fs, 0.0, 0.0))
                continue
            }
            val m = Stats.mean(x)
            for (i in x.indices) x[i] = (x[i] - m) * hann[i]
            val mag = Fft.rfftMagnitude(x)

            var fStep = -1.0
            var mStep = 0.0
            var fStride = -1.0
            var mStride = 0.0
            for (k in freqs.indices) {
                val f = freqs[k]
                if (f >= fMin && f <= fMax) {
                    if (fStep < 0 || mag[k] > mStep) { mStep = mag[k]; fStep = f }
                } else if (f >= strideFloor && f < fMin) {
                    if (fStride < 0 || mag[k] > mStride) { mStride = mag[k]; fStride = f }
                }
            }
            if (fStep < 0) continue
            if (fStride >= 0 && mStride > strideRatio * mStep && 2 * fStride >= fMin && 2 * fStride <= fMax) {
                fStep = 2 * fStride
                strideCorrections++
            }
            val steps = fStep * infl
            total += steps
            lastFreq = fStep
            windows.add(WindowCadence(start / fs, fStep, steps, mStep))
        }
        total += AlgorithmSupport.tailCorrection(lastFreq, n, fs, win, hop, count)
        val notes = ArrayList<Note>()
        if (strideCorrections > 0) notes.add(Note.StrideLocked(strideCorrections))
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = total.roundToInt(), stepIndices = IntArray(0),
            cadenceSpm = if (n > 0) total / (n / fs) * 60.0 else 0.0,
            params = mapOf(
                CoreDefaults.FftCadence.WINDOW_S.key to windowS,
                CoreDefaults.FftCadence.HOP_S.key to hopS,
                CoreDefaults.FftCadence.F_MIN.key to fMin,
                CoreDefaults.FftCadence.F_MAX.key to fMax,
                CoreDefaults.FftCadence.STD_FLOOR.key to stdFloor,
                CoreDefaults.FftCadence.STRIDE_FLOOR.key to strideFloor,
                CoreDefaults.FftCadence.STRIDE_RATIO.key to strideRatio,
            ),
            windows = windows,
            notes = notes,
        )
    }
}
