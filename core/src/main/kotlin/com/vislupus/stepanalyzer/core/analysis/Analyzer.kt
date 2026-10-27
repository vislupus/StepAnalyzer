package com.vislupus.stepanalyzer.core.analysis

import com.vislupus.stepanalyzer.core.algorithms.AlgorithmSupport
import com.vislupus.stepanalyzer.core.algorithms.Algorithms
import com.vislupus.stepanalyzer.core.algorithms.FftCadenceAlgorithm
import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.core.model.AnalysisResult
import com.vislupus.stepanalyzer.core.model.CadenceSeries
import com.vislupus.stepanalyzer.core.model.FileInfo
import com.vislupus.stepanalyzer.core.model.GpxSummary
import com.vislupus.stepanalyzer.core.model.Note
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.model.Recommendation
import com.vislupus.stepanalyzer.core.model.Sex
import com.vislupus.stepanalyzer.core.model.SummaryStats
import com.vislupus.stepanalyzer.core.signal.Stats
import com.vislupus.stepanalyzer.core.steplength.StepLengthEstimator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** User-tunable inputs of the analysis stage (everything after preprocessing). */
data class AnalysisParams(
    /** Parameter overrides per algorithm id; missing keys use the CoreDefaults. */
    val algorithmParams: Map<String, Map<String, Double>> = emptyMap(),
    /** Algorithm ids to run; null means all. */
    val enabledAlgorithms: Set<String>? = null,
    /** Force this algorithm as the step-length reference (must have step indices). */
    val referenceAlgorithmId: String? = null,
    val heightM: Double? = null,
    val sex: Sex = Sex.UNKNOWN,
)

/**
 * End-to-end pipeline: algorithms -> confidence -> recommendation -> step length -> cadence
 * series. Preprocessing is done once (see Preprocessor) and reused across parameter changes.
 */
object Analyzer {

    /** Strength (0..1) of the dominant walking-band autocorrelation peak of the whole signal. */
    fun globalPeriodicity(signal: DoubleArray, fs: Double, fMin: Double, fMax: Double): Double {
        val x = Stats.centred(signal)
        if (Stats.dot(x, x) <= 0) return 0.0
        val lo = max(1, (fs / fMax).toInt())
        val hi = (fs / fMin).toInt()
        if (hi <= lo) return 0.0
        val ac = Stats.autocorrelation(x, hi)
        var m = Double.NEGATIVE_INFINITY
        for (k in lo until minOf(hi, ac.size)) if (ac[k] > m) m = ac[k]
        if (m == Double.NEGATIVE_INFINITY) return 0.0
        return m.coerceIn(0.0, 1.0)
    }

    private fun amplitudeConsistency(signal: DoubleArray, indices: IntArray): Double {
        if (indices.size < 3) return 0.0
        val amps = DoubleArray(indices.size) { signal[indices[it]] }
        val m = Stats.meanAbs(amps)
        if (m == 0.0) return 0.0
        val cv = Stats.std(amps) / m
        return (1 - cv).coerceIn(0.0, 1.0)
    }

    /** Returns (score, impliedStepLength) for the GPX plausibility of a step count. */
    fun gpxConsistency(stepCount: Int, gpxDistance: Double?): Pair<Double, Double?> {
        if (gpxDistance == null || gpxDistance <= 0 || stepCount <= 0) return Pair(0.0, null)
        val l = gpxDistance / stepCount
        val c = CoreDefaults.Confidence
        val s = when {
            l >= c.IDEAL_MIN_M && l <= c.IDEAL_MAX_M -> 1.0
            l >= c.PLAUSIBLE_MIN_M && l <= c.PLAUSIBLE_MAX_M -> {
                val v = if (l < c.IDEAL_MIN_M) (l - c.PLAUSIBLE_MIN_M) / (c.IDEAL_MIN_M - c.PLAUSIBLE_MIN_M)
                else 1 - (l - c.IDEAL_MAX_M) / (c.PLAUSIBLE_MAX_M - c.IDEAL_MAX_M)
                v.coerceIn(0.0, 1.0)
            }
            else -> 0.0
        }
        return Pair(s, l)
    }

    private fun withConfidence(
        algo: AlgorithmResult, signal: DoubleArray, fs: Double, gpxDistance: Double?, globalPeriod: Double,
    ): AlgorithmResult {
        if (!algo.available) return algo
        val cv = if (algo.hasStepIndices) AlgorithmSupport.intervalCv(algo.stepIndices, fs) else Double.NaN
        val cInterval = if (cv.isFinite()) (1 - cv).coerceIn(0.0, 1.0) else globalPeriod
        val cAmp = if (algo.hasStepIndices) amplitudeConsistency(signal, algo.stepIndices) else globalPeriod
        val (cGpx, l) = gpxConsistency(algo.stepCount, gpxDistance)
        val c = CoreDefaults.Confidence
        val score = if (gpxDistance != null && gpxDistance > 0) {
            c.W_INTERVAL_GPX * cInterval + c.W_PERIOD_GPX * globalPeriod + c.W_GPX * cGpx + c.W_AMP_GPX * cAmp
        } else {
            c.W_INTERVAL * cInterval + c.W_PERIOD * globalPeriod + c.W_AMP * cAmp
        }
        val notes = ArrayList(algo.notes)
        if (globalPeriod > c.STRONG_PERIODICITY) notes.add(Note.StrongPeriodicity)
        if (cv.isFinite()) notes.add(Note.IntervalCv(cv))
        if (l != null) {
            notes.add(Note.GpxStepLength(l))
            if (l < c.PLAUSIBLE_MIN_M || l > c.PLAUSIBLE_MAX_M) notes.add(Note.OutOfPlausibleRange)
        }
        if (algo.family == AlgorithmFamily.FREQUENCY) notes.add(Note.FrequencyMethod)
        return algo.copy(
            confidence = ((score.coerceIn(0.0, 1.0)) * 100).roundToInt() / 100.0,
            notes = notes,
            avgStepLengthGpxM = l,
        )
    }

    /** Run every enabled algorithm with the given parameters (no scoring). */
    fun runAlgorithms(pre: Preprocessed, params: AnalysisParams): List<AlgorithmResult> =
        Algorithms.all
            .filter { params.enabledAlgorithms == null || it.id in params.enabledAlgorithms }
            .map { it.run(pre, params.algorithmParams[it.id] ?: emptyMap()) }

    fun analyze(pre: Preprocessed, gpx: GpxSummary?, params: AnalysisParams = AnalysisParams()): AnalysisResult {
        val signal = pre.aFilt
        val fs = pre.fs
        val gpxDistance = gpx?.distanceM?.takeIf { it > 0 }

        val globalPeriod = globalPeriodicity(
            signal, fs,
            fMin = pre.options.bandLowHz + 0.1,
            fMax = pre.options.bandHighHz,
        )
        val algos = runAlgorithms(pre, params).map { withConfidence(it, signal, fs, gpxDistance, globalPeriod) }
        val available = algos.filter { it.available }
        require(available.isNotEmpty()) { "No algorithm could run on this recording" }

        // recommended = highest confidence
        val best = available.maxByOrNull { it.confidence }!!

        // reference for step-length windows: prefer explicit choice, else a peak-based
        // algorithm within REFERENCE_MARGIN of the best (needs explicit step indices)
        val withIndices = available.filter { it.hasStepIndices }
        var ref = best
        params.referenceAlgorithmId?.let { id -> withIndices.firstOrNull { it.id == id }?.let { ref = it } }
        if (params.referenceAlgorithmId == null || ref === best) {
            if (best.family == AlgorithmFamily.FREQUENCY && withIndices.isNotEmpty()) {
                val cand = withIndices.maxByOrNull { it.confidence }!!
                if (cand.confidence >= best.confidence - CoreDefaults.Confidence.REFERENCE_MARGIN) ref = cand
            }
            if (!ref.hasStepIndices && withIndices.isNotEmpty()) ref = withIndices.maxByOrNull { it.confidence }!!
        }

        val stepLength = StepLengthEstimator.compute(
            aDyn = pre.aDyn, fs = fs,
            referenceAlgorithmId = ref.id,
            refIndices = ref.stepIndices, nSteps = ref.stepCount, cadenceSpm = ref.cadenceSpm,
            gpxDistance = gpxDistance, heightM = params.heightM, sex = params.sex,
        )

        val fft = algos.firstOrNull { it.id == FftCadenceAlgorithm.ID && it.available }
        val cadence = if (fft != null && fft.windows.isNotEmpty()) {
            CadenceSeries(
                DoubleArray(fft.windows.size) { fft.windows[it].tSec },
                DoubleArray(fft.windows.size) { fft.windows[it].freqHz * 60.0 },
            )
        } else CadenceSeries(DoubleArray(0), DoubleArray(0))

        val counts = available.map { it.stepCount.toDouble() }.toDoubleArray()
        val summary = SummaryStats(
            minSteps = counts.min().toInt(),
            maxSteps = counts.max().toInt(),
            medianSteps = Stats.median(counts).toInt(),
            spread = (counts.max() - counts.min()).toInt(),
            globalPeriodicity = globalPeriod,
        )
        val (cGpx, _) = gpxConsistency(best.stepCount, gpxDistance)
        return AnalysisResult(
            fileInfo = FileInfo(
                sampleRateHz = pre.fs,
                durationSec = pre.durationS,
                rows = pre.nRowsRaw,
                sensorsFound = pre.sensorsFound,
                nativeRatesHz = pre.nativeRates,
                dynamicSource = pre.dynamicSource,
            ),
            gpx = gpx,
            algorithms = algos,
            stepLength = stepLength,
            recommended = Recommendation(
                algorithmId = best.id,
                stepCount = best.stepCount,
                confidence = best.confidence,
                family = best.family,
                gpxConsistent = gpxDistance != null && cGpx > 0.5,
            ),
            summary = summary,
            cadence = cadence,
            referenceAlgorithmId = ref.id,
        )
    }

    /** Agreement (0..1) between two step counts, used by the comparison view. */
    fun agreement(a: Int, b: Int): Double {
        if (a <= 0 && b <= 0) return 1.0
        val m = max(a, b).toDouble()
        return (1 - abs(a - b) / m).coerceIn(0.0, 1.0)
    }
}
