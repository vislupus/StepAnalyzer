package com.vislupus.stepanalyzer.core.algorithms

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.core.model.ParamSpec
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.signal.IntList
import com.vislupus.stepanalyzer.core.signal.Peaks
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.abs
import kotlin.math.max

/**
 * Algorithm A - peak detection on the band-passed acceleration magnitude.
 * Counts local maxima that are at least `min_interval_s` apart and whose prominence exceeds
 * max(prominence_floor, prominence_factor * std).
 */
object PeakAlgorithm : StepAlgorithm {
    const val ID = "peak_magnitude"
    override val id = ID
    override val family = AlgorithmFamily.PEAK
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.Peak.MIN_INTERVAL_S, CoreDefaults.Peak.PROMINENCE_FACTOR, CoreDefaults.Peak.PROMINENCE_FLOOR,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val signal = pre.aFilt
        val fs = pre.fs
        val minInterval = param(params, CoreDefaults.Peak.MIN_INTERVAL_S)
        val promFactor = param(params, CoreDefaults.Peak.PROMINENCE_FACTOR)
        val promFloor = param(params, CoreDefaults.Peak.PROMINENCE_FLOOR)
        val minDist = max(1, (minInterval * fs).toInt())
        val prominence = max(promFloor, promFactor * Stats.std(signal))
        val peaks = Peaks.findPeaks(signal, distance = minDist, prominence = prominence)
        val active = AlgorithmSupport.activeDuration(signal, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = peaks.size, stepIndices = peaks,
            cadenceSpm = AlgorithmSupport.cadence(peaks.size, active),
            params = mapOf(
                CoreDefaults.Peak.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.Peak.PROMINENCE_FACTOR.key to promFactor,
                CoreDefaults.Peak.PROMINENCE_FLOOR.key to promFloor,
                "prominence_used" to prominence,
            ),
        )
    }
}

/**
 * Algorithm B - adaptive peak-valley pairs. A peak only counts when the nearest valley is
 * deep enough: peak - valley > max(threshold_floor, std_factor * local std).
 */
object PeakValleyAlgorithm : StepAlgorithm {
    const val ID = "adaptive_peak_valley"
    override val id = ID
    override val family = AlgorithmFamily.PEAK
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.PeakValley.MIN_INTERVAL_S, CoreDefaults.PeakValley.STD_FACTOR,
        CoreDefaults.PeakValley.THRESHOLD_FLOOR, CoreDefaults.PeakValley.PAIR_WINDOW_S,
        CoreDefaults.PeakValley.LOCAL_WINDOW_S,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val signal = pre.aFilt
        val fs = pre.fs
        val minInterval = param(params, CoreDefaults.PeakValley.MIN_INTERVAL_S)
        val stdFactor = param(params, CoreDefaults.PeakValley.STD_FACTOR)
        val floor = param(params, CoreDefaults.PeakValley.THRESHOLD_FLOOR)
        val pairWindowS = param(params, CoreDefaults.PeakValley.PAIR_WINDOW_S)
        val localWindowS = param(params, CoreDefaults.PeakValley.LOCAL_WINDOW_S)

        val minDist = max(1, (minInterval * fs).toInt())
        val peaks = Peaks.findPeaks(signal, distance = minDist)
        val negated = DoubleArray(signal.size) { -signal[it] }
        val valleys = Peaks.findPeaks(negated, distance = minDist)

        val accepted = IntList()
        val winHalf = (localWindowS * fs).toInt()
        val pairWin = (pairWindowS * fs).toInt()
        var last = Int.MIN_VALUE / 2
        var vCursor = 0
        for (p in peaks) {
            if (p - last < minDist) continue
            // nearest valley within +-pairWin (valleys are sorted)
            while (vCursor < valleys.size && valleys[vCursor] < p - pairWin) vCursor++
            var best = -1
            var bestDist = Int.MAX_VALUE
            var k = vCursor
            while (k < valleys.size && valleys[k] <= p + pairWin) {
                val d = abs(valleys[k] - p)
                if (d < bestDist) { bestDist = d; best = valleys[k] }
                k++
            }
            if (best < 0) continue
            val lo = max(0, p - winHalf)
            val hi = minOf(signal.size, p + winHalf)
            val localStd = Stats.std(signal, lo, hi)
            val thr = max(floor, stdFactor * localStd)
            if (signal[p] - signal[best] > thr) {
                accepted.add(p)
                last = p
            }
        }
        val idx = accepted.toArray()
        val active = AlgorithmSupport.activeDuration(signal, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = idx.size, stepIndices = idx,
            cadenceSpm = AlgorithmSupport.cadence(idx.size, active),
            params = mapOf(
                CoreDefaults.PeakValley.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.PeakValley.STD_FACTOR.key to stdFactor,
                CoreDefaults.PeakValley.THRESHOLD_FLOOR.key to floor,
                CoreDefaults.PeakValley.PAIR_WINDOW_S.key to pairWindowS,
                CoreDefaults.PeakValley.LOCAL_WINDOW_S.key to localWindowS,
            ),
        )
    }
}

/**
 * Algorithm C - positive-going zero crossings validated by a following peak above
 * max(threshold_floor, threshold_factor * std) within `lookahead_s`.
 */
object ZeroCrossingAlgorithm : StepAlgorithm {
    const val ID = "zero_crossing"
    override val id = ID
    override val family = AlgorithmFamily.ZERO_CROSSING
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.ZeroCrossing.MIN_INTERVAL_S, CoreDefaults.ZeroCrossing.THRESHOLD_FACTOR,
        CoreDefaults.ZeroCrossing.THRESHOLD_FLOOR, CoreDefaults.ZeroCrossing.LOOKAHEAD_S,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val signal = pre.aFilt
        val fs = pre.fs
        val minInterval = param(params, CoreDefaults.ZeroCrossing.MIN_INTERVAL_S)
        val thrFactor = param(params, CoreDefaults.ZeroCrossing.THRESHOLD_FACTOR)
        val floor = param(params, CoreDefaults.ZeroCrossing.THRESHOLD_FLOOR)
        val lookaheadS = param(params, CoreDefaults.ZeroCrossing.LOOKAHEAD_S)

        val s = Stats.centred(signal)
        val thr = max(floor, thrFactor * Stats.std(s))
        val minInt = max(1, (minInterval * fs).toInt())
        val look = max(1, (lookaheadS * fs).toInt())
        val steps = IntList()
        var last = Int.MIN_VALUE / 2
        for (i in 1 until s.size) {
            if (!(s[i - 1] < 0 && s[i] >= 0)) continue
            if (i - last < minInt) continue
            val end = minOf(s.size, i + look)
            if (end > i && Stats.max(s, i, end) > thr) {
                steps.add(i)
                last = i
            }
        }
        val idx = steps.toArray()
        val active = AlgorithmSupport.activeDuration(signal, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = idx.size, stepIndices = idx,
            cadenceSpm = AlgorithmSupport.cadence(idx.size, active),
            params = mapOf(
                CoreDefaults.ZeroCrossing.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.ZeroCrossing.THRESHOLD_FACTOR.key to thrFactor,
                CoreDefaults.ZeroCrossing.THRESHOLD_FLOOR.key to floor,
                CoreDefaults.ZeroCrossing.LOOKAHEAD_S.key to lookaheadS,
                "threshold_used" to thr,
            ),
        )
    }
}
