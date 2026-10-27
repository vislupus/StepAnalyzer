package com.vislupus.stepanalyzer.core.steplength

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.Requirement
import com.vislupus.stepanalyzer.core.model.Sex
import com.vislupus.stepanalyzer.core.model.StepLengthFamily
import com.vislupus.stepanalyzer.core.model.StepLengthMethod
import com.vislupus.stepanalyzer.core.model.StepLengthResult
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.max
import kotlin.math.pow

/**
 * Step-length estimation by seven methods in three families:
 *
 *  GPS-anchored
 *    1. GPX average                 L = D_gpx / N
 *  Acceleration-amplitude models (per step, coefficient K)
 *    2. Weinberg                    L = K * (a_max - a_min)^(1/4)
 *    3. Kim                         L = K * (mean|a|)^(1/3)
 *    4. Scarlett                    L = K * (mean|a| - a_min) / (a_max - a_min)
 *  Biomechanical / statistical
 *    5. Height factor               L = c * height
 *    6. Cadence linear              L = a * f_step + b
 *    7. Height x cadence            L = height * (a * f_step + b)   (Renaudin-style)
 *
 * The amplitude models report the literature-default K estimate and, when a GPX distance
 * exists, the K that makes the model reproduce the GPS distance.
 */
object StepLengthEstimator {
    const val GPX_AVG = "gpx_avg"
    const val WEINBERG = "weinberg"
    const val KIM = "kim"
    const val SCARLETT = "scarlett"
    const val HEIGHT_FACTOR = "height_factor"
    const val CADENCE_LINEAR = "cadence_linear"
    const val HEIGHT_CADENCE = "height_cadence"
    const val SOURCE_MEDIAN = "median_of_methods"

    private class Features(val range: DoubleArray, val meanAbs: DoubleArray, val scarlett: DoubleArray)

    /** Per-step amplitude features between consecutive step indices. */
    private fun segmentFeatures(aDyn: DoubleArray, fs: Double, indices: IntArray): Features? {
        if (indices.size < 2) return null
        val idx = indices.copyOf()
        idx.sort()
        val bounds = IntArray(idx.size + 1)
        System.arraycopy(idx, 0, bounds, 0, idx.size)
        bounds[idx.size] = minOf(aDyn.size, idx[idx.size - 1] + (CoreDefaults.StepLength.LAST_SEGMENT_S * fs).toInt())
        val range = ArrayList<Double>(idx.size)
        val meanAbs = ArrayList<Double>(idx.size)
        val scar = ArrayList<Double>(idx.size)
        for (k in 0 until bounds.size - 1) {
            val a = bounds[k]
            val b = bounds[k + 1]
            if (b - a < 2) continue
            val amax = Stats.max(aDyn, a, b)
            val amin = Stats.min(aDyn, a, b)
            val rng = amax - amin
            val mabs = Stats.meanAbs(aDyn, a, b)
            range.add(rng)
            meanAbs.add(mabs)
            scar.add(if (rng > 1e-6) (mabs - amin) / rng else 0.0)
        }
        if (range.isEmpty()) return null
        return Features(range.toDoubleArray(), meanAbs.toDoubleArray(), scar.toDoubleArray())
    }

    private fun amplitudeMethod(
        id: String, values: DoubleArray, transform: (Double) -> Double, kDefault: Double,
        nSteps: Int, gpxDistance: Double?,
    ): StepLengthMethod? {
        val base = values.map(transform).filter { it.isFinite() }.toDoubleArray()
        if (base.isEmpty()) return null
        val meanBase = Stats.mean(base)
        val sumBase = base.sum()
        val lDefault = kDefault * meanBase
        var kCal: Double? = null
        var lCal: Double? = null
        if (gpxDistance != null && gpxDistance > 0 && sumBase > 0) {
            kCal = gpxDistance / sumBase
            lCal = kCal * meanBase
        }
        val primary = lCal ?: lDefault
        return StepLengthMethod(
            id = id,
            family = StepLengthFamily.AMPLITUDE,
            stepLengthM = primary,
            distanceM = if (nSteps > 0) primary * nSteps else null,
            calibrated = lCal != null,
            requires = if (lCal != null) Requirement.ACCELEROMETER else Requirement.ACCELEROMETER_AND_CALIBRATION,
            uncalibratedM = lDefault,
            gpxCalibratedM = lCal,
            calibratedK = kCal,
            defaultK = kDefault,
        )
    }

    fun heightFactor(sex: Sex): Double = when (sex) {
        Sex.MALE -> CoreDefaults.StepLength.HEIGHT_FACTOR_MALE
        Sex.FEMALE -> CoreDefaults.StepLength.HEIGHT_FACTOR_FEMALE
        Sex.UNKNOWN -> CoreDefaults.StepLength.HEIGHT_FACTOR_UNKNOWN
    }

    fun compute(
        aDyn: DoubleArray,
        fs: Double,
        referenceAlgorithmId: String,
        refIndices: IntArray,
        nSteps: Int,
        cadenceSpm: Double,
        gpxDistance: Double?,
        heightM: Double?,
        sex: Sex,
    ): StepLengthResult {
        val methods = ArrayList<StepLengthMethod>()
        val fStep = if (cadenceSpm > 0) cadenceSpm / 60.0 else 0.0
        val h = if (heightM != null && heightM > 0) heightM else null
        val hUsed = h ?: CoreDefaults.StepLength.DEFAULT_HEIGHT_M
        val heightReq = if (h != null) Requirement.HEIGHT else Requirement.HEIGHT_DEFAULT

        // 1. GPX average
        if (gpxDistance != null && gpxDistance > 0 && nSteps > 0) {
            methods.add(
                StepLengthMethod(
                    id = GPX_AVG, family = StepLengthFamily.GPS,
                    stepLengthM = gpxDistance / nSteps, distanceM = gpxDistance,
                    calibrated = true, requires = Requirement.GPX,
                )
            )
        }

        // 2-4. amplitude models
        val feats = if (refIndices.size >= 2) segmentFeatures(aDyn, fs, refIndices) else null
        if (feats != null) {
            amplitudeMethod(WEINBERG, feats.range, { max(0.0, it).pow(0.25) }, CoreDefaults.StepLength.K_WEINBERG, nSteps, gpxDistance)?.let(methods::add)
            amplitudeMethod(KIM, feats.meanAbs, { max(1e-9, it).pow(1.0 / 3.0) }, CoreDefaults.StepLength.K_KIM, nSteps, gpxDistance)?.let(methods::add)
            amplitudeMethod(SCARLETT, feats.scarlett, { max(0.0, it) }, CoreDefaults.StepLength.K_SCARLETT, nSteps, gpxDistance)?.let(methods::add)
        }

        // 5. height factor
        val c = heightFactor(sex)
        val lHeight = c * hUsed
        methods.add(
            StepLengthMethod(
                id = HEIGHT_FACTOR, family = StepLengthFamily.BIOMECHANICAL,
                stepLengthM = lHeight, distanceM = if (nSteps > 0) lHeight * nSteps else null,
                calibrated = false, requires = heightReq, coefficient = c,
            )
        )

        if (fStep > 0) {
            // 6. cadence linear
            val lCad = CoreDefaults.StepLength.CADENCE_A * fStep + CoreDefaults.StepLength.CADENCE_B
            methods.add(
                StepLengthMethod(
                    id = CADENCE_LINEAR, family = StepLengthFamily.BIOMECHANICAL,
                    stepLengthM = lCad, distanceM = if (nSteps > 0) lCad * nSteps else null,
                    calibrated = false, requires = Requirement.NONE,
                )
            )
            // 7. height x cadence
            val lHc = hUsed * (CoreDefaults.StepLength.HEIGHT_CADENCE_A * fStep + CoreDefaults.StepLength.HEIGHT_CADENCE_B)
            methods.add(
                StepLengthMethod(
                    id = HEIGHT_CADENCE, family = StepLengthFamily.BIOMECHANICAL,
                    stepLengthM = lHc, distanceM = if (nSteps > 0) lHc * nSteps else null,
                    calibrated = false, requires = heightReq,
                )
            )
        }

        val recommended: Double?
        val source: String
        if (gpxDistance != null && gpxDistance > 0 && nSteps > 0) {
            recommended = gpxDistance / nSteps
            source = GPX_AVG
        } else {
            val vals = methods.map { it.stepLengthM }.filter { it > 0 }.toDoubleArray()
            recommended = if (vals.isNotEmpty()) Stats.median(vals) else null
            source = SOURCE_MEDIAN
        }
        return StepLengthResult(
            methods = methods,
            recommendedM = recommended,
            recommendedSource = source,
            referenceAlgorithmId = referenceAlgorithmId,
            referenceStepCount = nSteps,
            heightUsedM = hUsed,
            heightWasDefault = h == null,
        )
    }
}
