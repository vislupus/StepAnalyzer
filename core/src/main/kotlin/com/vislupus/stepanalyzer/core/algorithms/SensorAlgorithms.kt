package com.vislupus.stepanalyzer.core.algorithms

import com.vislupus.stepanalyzer.core.analysis.Preprocessor
import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AlgorithmFamily
import com.vislupus.stepanalyzer.core.model.AlgorithmResult
import com.vislupus.stepanalyzer.core.model.ParamSpec
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.model.SensorKind
import com.vislupus.stepanalyzer.core.signal.Filters
import com.vislupus.stepanalyzer.core.signal.Peaks
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Algorithm H - peak detection on the band-passed gyroscope magnitude. Useful when the phone
 * swings in the hand; depending on placement it may lock onto strides (every second step).
 */
object GyroPeakAlgorithm : StepAlgorithm {
    const val ID = "gyro_peak"
    override val id = ID
    override val family = AlgorithmFamily.PEAK
    override val requires: Set<SensorKind> = setOf(SensorKind.GYRO)
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.GyroPeak.MIN_INTERVAL_S, CoreDefaults.GyroPeak.PROMINENCE_FACTOR,
        CoreDefaults.GyroPeak.PROMINENCE_FLOOR,
    )

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val miss = missing(pre)
        val gyroMag = pre.gyroMag
        if (miss.isNotEmpty() || gyroMag == null) return unavailable(params, miss.ifEmpty { listOf(SensorKind.GYRO) })
        val fs = pre.fs
        val minInterval = param(params, CoreDefaults.GyroPeak.MIN_INTERVAL_S)
        val promFactor = param(params, CoreDefaults.GyroPeak.PROMINENCE_FACTOR)
        val promFloor = param(params, CoreDefaults.GyroPeak.PROMINENCE_FLOOR)

        val med = Stats.median(gyroMag)
        val dyn = DoubleArray(gyroMag.size) { gyroMag[it] - med }
        val filt = Preprocessor.bandpass(dyn, fs, pre.options.bandLowHz, pre.options.bandHighHz, pre.options.filterOrder)
        val minDist = max(1, (minInterval * fs).toInt())
        val prominence = max(promFloor, promFactor * Stats.std(filt))
        val peaks = Peaks.findPeaks(filt, distance = minDist, prominence = prominence)
        val active = AlgorithmSupport.activeDuration(pre.aFilt, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = peaks.size, stepIndices = peaks,
            cadenceSpm = AlgorithmSupport.cadence(peaks.size, active),
            params = mapOf(
                CoreDefaults.GyroPeak.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.GyroPeak.PROMINENCE_FACTOR.key to promFactor,
                CoreDefaults.GyroPeak.PROMINENCE_FLOOR.key to promFloor,
                "prominence_used" to prominence,
            ),
            signal = filt,
        )
    }
}

/**
 * Algorithm I - vertical acceleration. Gravity is estimated with a slow low-pass filter on
 * the raw accelerometer vector; the acceleration is projected onto that direction, which
 * gives a sign-consistent vertical signal that ignores horizontal sway. The projection is
 * then band-passed and peak-detected.
 */
object VerticalAccelAlgorithm : StepAlgorithm {
    const val ID = "vertical_accel"
    override val id = ID
    override val family = AlgorithmFamily.PEAK
    override val requires: Set<SensorKind> = setOf(SensorKind.ACCEL)
    override val paramSpecs: List<ParamSpec> = listOf(
        CoreDefaults.VerticalAccel.GRAVITY_CUTOFF_HZ, CoreDefaults.VerticalAccel.MIN_INTERVAL_S,
        CoreDefaults.VerticalAccel.PROMINENCE_FACTOR, CoreDefaults.VerticalAccel.PROMINENCE_FLOOR,
    )

    /** Vertical component of the acceleration (gravity removed) for the given stream. */
    fun verticalSignal(pre: Preprocessed, gravityCutoffHz: Double): DoubleArray? {
        val acc = pre.streams[SensorKind.ACCEL] ?: return null
        val fs = pre.fs
        val gx = Filters.emaLowPass(acc.x, fs, gravityCutoffHz)
        val gy = Filters.emaLowPass(acc.y, fs, gravityCutoffHz)
        val gz = Filters.emaLowPass(acc.z, fs, gravityCutoffHz)
        val n = acc.size
        return DoubleArray(n) { i ->
            val gm = sqrt(gx[i] * gx[i] + gy[i] * gy[i] + gz[i] * gz[i])
            if (gm < 1e-6) 0.0 else (acc.x[i] * gx[i] + acc.y[i] * gy[i] + acc.z[i] * gz[i]) / gm - gm
        }
    }

    override fun run(pre: Preprocessed, params: Map<String, Double>): AlgorithmResult {
        val miss = missing(pre)
        if (miss.isNotEmpty()) return unavailable(params, miss)
        val fs = pre.fs
        val cutoff = param(params, CoreDefaults.VerticalAccel.GRAVITY_CUTOFF_HZ)
        val minInterval = param(params, CoreDefaults.VerticalAccel.MIN_INTERVAL_S)
        val promFactor = param(params, CoreDefaults.VerticalAccel.PROMINENCE_FACTOR)
        val promFloor = param(params, CoreDefaults.VerticalAccel.PROMINENCE_FLOOR)

        val vertical = verticalSignal(pre, cutoff) ?: return unavailable(params, listOf(SensorKind.ACCEL))
        val filt = Preprocessor.bandpass(vertical, fs, pre.options.bandLowHz, pre.options.bandHighHz, pre.options.filterOrder)
        val minDist = max(1, (minInterval * fs).toInt())
        val prominence = max(promFloor, promFactor * Stats.std(filt))
        val peaks = Peaks.findPeaks(filt, distance = minDist, prominence = prominence)
        val active = AlgorithmSupport.activeDuration(filt, fs)
        return AlgorithmResult(
            id = id, family = family, available = true,
            stepCount = peaks.size, stepIndices = peaks,
            cadenceSpm = AlgorithmSupport.cadence(peaks.size, active),
            params = mapOf(
                CoreDefaults.VerticalAccel.GRAVITY_CUTOFF_HZ.key to cutoff,
                CoreDefaults.VerticalAccel.MIN_INTERVAL_S.key to minInterval,
                CoreDefaults.VerticalAccel.PROMINENCE_FACTOR.key to promFactor,
                CoreDefaults.VerticalAccel.PROMINENCE_FLOOR.key to promFloor,
                "prominence_used" to prominence,
            ),
            signal = filt,
        )
    }
}
