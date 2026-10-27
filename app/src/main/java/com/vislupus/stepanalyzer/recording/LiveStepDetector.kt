package com.vislupus.stepanalyzer.recording

import com.vislupus.stepanalyzer.config.AppConfig
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Lightweight streaming step detector for the live counter shown while recording.
 * magnitude -> one-pole high-pass (removes gravity) -> one-pole low-pass -> Schmitt trigger.
 * It is intentionally simple; the real analysis happens afterwards on the saved file.
 */
class LiveStepDetector(
    private val thresholdMps2: Double = AppConfig.Recording.Live.THRESHOLD_MPS2,
    private val minIntervalMs: Long = AppConfig.Recording.Live.MIN_INTERVAL_MS,
    private val highpassTauS: Double = AppConfig.Recording.Live.HIGHPASS_TAU_S,
    private val smoothTauS: Double = AppConfig.Recording.Live.SMOOTH_TAU_S,
) {
    var steps = 0
        private set

    private var lastTimestampNs = 0L
    private var lowpass = Double.NaN      // slow average of the magnitude (gravity estimate)
    private var smooth = 0.0
    private var armed = false
    private var lastStepMs = Long.MIN_VALUE / 2

    /** The latest filtered sample (gravity-free, smoothed) for the live chart. */
    var lastFiltered = 0f
        private set

    fun reset() {
        steps = 0
        lastTimestampNs = 0L
        lowpass = Double.NaN
        smooth = 0.0
        armed = false
        lastStepMs = Long.MIN_VALUE / 2
        lastFiltered = 0f
    }

    /** Feed one accelerometer sample. Returns true when a new step was counted. */
    fun onSample(timestampNs: Long, x: Float, y: Float, z: Float): Boolean {
        val mag = sqrt((x * x + y * y + z * z).toDouble())
        if (lowpass.isNaN()) {
            lowpass = mag
            lastTimestampNs = timestampNs
            return false
        }
        var dt = (timestampNs - lastTimestampNs) / 1e9
        if (dt <= 0 || dt > 0.5) dt = 0.01
        lastTimestampNs = timestampNs
        val aSlow = 1 - exp(-dt / highpassTauS)
        lowpass += aSlow * (mag - lowpass)
        val hp = mag - lowpass
        val aFast = 1 - exp(-dt / smoothTauS)
        smooth += aFast * (hp - smooth)
        lastFiltered = smooth.toFloat()

        val nowMs = timestampNs / 1_000_000
        if (!armed) {
            if (smooth < -thresholdMps2) armed = true
        } else if (smooth > thresholdMps2) {
            armed = false
            if (nowMs - lastStepMs >= minIntervalMs) {
                lastStepMs = nowMs
                steps++
                return true
            }
        }
        return false
    }
}
