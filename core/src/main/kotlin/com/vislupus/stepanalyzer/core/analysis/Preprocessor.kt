package com.vislupus.stepanalyzer.core.analysis

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.AccelUnit
import com.vislupus.stepanalyzer.core.model.GyroUnit
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.core.model.Preprocessed
import com.vislupus.stepanalyzer.core.model.RawData
import com.vislupus.stepanalyzer.core.model.RawStream
import com.vislupus.stepanalyzer.core.model.SensorKind
import com.vislupus.stepanalyzer.core.model.Stream
import com.vislupus.stepanalyzer.core.signal.Butterworth
import com.vislupus.stepanalyzer.core.signal.Filters
import com.vislupus.stepanalyzer.core.signal.Interp
import com.vislupus.stepanalyzer.core.signal.Stats
import kotlin.math.max

/**
 * Turns raw, irregularly sampled sensor streams into uniform signals on a common time grid
 * and derives the band-passed dynamic acceleration that every step algorithm consumes.
 */
object Preprocessor {

    /** Zero-phase Butterworth band-pass; degenerates to a linear detrend at very low fs. */
    fun bandpass(x: DoubleArray, fs: Double, lowHz: Double, highHz: Double, order: Int): DoubleArray {
        val nyq = 0.5 * fs
        val hi = minOf(highHz, nyq * 0.99) / nyq
        val lo = max(lowHz, 1e-3) / nyq
        if (lo >= hi) return Filters.detrendLinear(x)
        return Filters.filtfilt(Butterworth.bandpass(order, lo, hi), x)
    }

    private fun nativeRate(t: DoubleArray): Double {
        if (t.size < 2) return Double.NaN
        val sorted = t.copyOf()
        sorted.sort()
        val dts = ArrayList<Double>(t.size)
        for (i in 1 until sorted.size) {
            val dt = sorted[i] - sorted[i - 1]
            if (dt > 0) dts.add(dt)
        }
        if (dts.isEmpty()) return Double.NaN
        return 1.0 / Stats.median(dts.toDoubleArray())
    }

    private fun resample(raw: RawStream, tOffset: Double, grid: DoubleArray): Stream {
        val shifted = DoubleArray(raw.size) { raw.t[it] - tOffset }
        val clean = Interp.sortUnique(shifted, raw.x, raw.y, raw.z)
        return Stream(
            Interp.interp(grid, clean[0], clean[1]),
            Interp.interp(grid, clean[0], clean[2]),
            Interp.interp(grid, clean[0], clean[3]),
        )
    }

    fun preprocess(raw: RawData, options: PreprocessOptions = PreprocessOptions()): Preprocessed {
        require(raw.streams.containsKey(SensorKind.ACCEL) || raw.streams.containsKey(SensorKind.UACCEL)) {
            "No accelerometer stream in the data"
        }
        val fs = options.fsTarget
        val tMin = raw.streams.values.minOf { s -> s.t.min() }
        val tMax = raw.streams.values.maxOf { s -> s.t.max() }
        val duration = tMax - tMin
        var grid = Interp.arange(duration, 1.0 / fs)
        if (grid.size < 8) grid = Interp.linspace(0.0, duration, max(8, (duration * fs).toInt()))

        val native = HashMap<SensorKind, Double>()
        val streams = HashMap<SensorKind, Stream>()
        for ((kind, rs) in raw.streams) {
            native[kind] = nativeRate(rs.t)
            streams[kind] = resample(rs, tMin, grid)
        }

        // --- accelerometer unit detection (g vs m/s^2) ---
        val accKind = if (streams.containsKey(SensorKind.ACCEL)) SensorKind.ACCEL else SensorKind.UACCEL
        var accelUnit = options.accelUnit
        if (accelUnit == AccelUnit.AUTO) {
            val typical = Stats.median(streams.getValue(accKind).magnitude())
            accelUnit = if (typical > CoreDefaults.Preprocess.G_UNIT_MIN && typical < CoreDefaults.Preprocess.G_UNIT_MAX) AccelUnit.G else AccelUnit.MPS2
        }
        if (accelUnit == AccelUnit.G) {
            for (k in listOf(SensorKind.ACCEL, SensorKind.UACCEL)) {
                streams[k]?.let { streams[k] = it.scaled(CoreDefaults.G) }
            }
        }

        // --- dynamic (gravity-free) magnitude ---
        val accMagRaw: DoubleArray
        val aDyn: DoubleArray
        val source: SensorKind
        if (streams.containsKey(SensorKind.ACCEL)) {
            accMagRaw = streams.getValue(SensorKind.ACCEL).magnitude()
            val med = Stats.median(accMagRaw)
            aDyn = DoubleArray(accMagRaw.size) { accMagRaw[it] - med }
            source = SensorKind.ACCEL
        } else {
            val m = streams.getValue(SensorKind.UACCEL).magnitude()
            accMagRaw = DoubleArray(m.size) { m[it] + CoreDefaults.G }
            val med = Stats.median(m)
            aDyn = DoubleArray(m.size) { m[it] - med }
            source = SensorKind.UACCEL
        }
        val aFilt = bandpass(aDyn, fs, options.bandLowHz, options.bandHighHz, options.filterOrder)

        // --- gyroscope unit detection (rad/s vs deg/s) ---
        var gyroMag: DoubleArray? = null
        var gyroUnit: GyroUnit? = null
        streams[SensorKind.GYRO]?.let { g ->
            val gm = g.magnitude()
            var unit = options.gyroUnit
            if (unit == GyroUnit.AUTO) {
                unit = if (Stats.percentile(gm, 99.0) > CoreDefaults.Preprocess.GYRO_DEGPS_THRESHOLD) GyroUnit.DEGPS else GyroUnit.RADPS
            }
            if (unit == GyroUnit.DEGPS) {
                streams[SensorKind.GYRO] = g.scaled(Math.PI / 180.0)
                for (i in gm.indices) gm[i] = Math.toRadians(gm[i])
            }
            gyroMag = gm
            gyroUnit = unit
        }

        return Preprocessed(
            options = options,
            fs = fs,
            t = grid,
            durationS = duration,
            nRowsRaw = raw.nRows,
            nativeRates = native,
            streams = streams,
            accMagRaw = accMagRaw,
            aDyn = aDyn,
            aFilt = aFilt,
            gyroMag = gyroMag,
            dynamicSource = source,
            accelUnitDetected = accelUnit,
            gyroUnitDetected = gyroUnit,
        )
    }
}
