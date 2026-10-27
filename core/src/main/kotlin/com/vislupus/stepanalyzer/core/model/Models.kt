package com.vislupus.stepanalyzer.core.model

import kotlin.math.sqrt

/** The sensor streams the analyser understands. */
enum class SensorKind(val csvName: String) {
    ACCEL("accel"),     // accelerometer including gravity
    UACCEL("uaccel"),   // linear acceleration (gravity removed by the device)
    GYRO("gyro"),
    MAG("mag");

    companion object {
        /** Map any of the accepted aliases in a CSV `sensor` column to a canonical kind. */
        fun fromAlias(raw: String): SensorKind? {
            val key = raw.lowercase().filter { it in 'a'..'z' }
            return when (key) {
                "accel", "acc", "acceleration", "accelerometer", "a", "rawaccel" -> ACCEL
                "uaccel", "linaccel", "linear", "linearacceleration", "useraccel", "la" -> UACCEL
                "gyro", "gyr", "gyroscope", "g" -> GYRO
                "mag", "magnetometer", "magnetic", "m" -> MAG
                else -> null
            }
        }
    }
}

/** Raw (non-uniform) samples of one sensor: time in seconds plus three axes. */
class RawStream(val t: DoubleArray, val x: DoubleArray, val y: DoubleArray, val z: DoubleArray) {
    val size: Int get() = t.size
}

/** Parsed CSV content: one raw stream per sensor kind found. */
class RawData(val nRows: Int, val streams: Map<SensorKind, RawStream>)

/** A sensor stream resampled onto the common uniform time grid. */
class Stream(val x: DoubleArray, val y: DoubleArray, val z: DoubleArray) {
    val size: Int get() = x.size

    fun magnitude(): DoubleArray = DoubleArray(size) { sqrt(x[it] * x[it] + y[it] * y[it] + z[it] * z[it]) }

    fun scaled(k: Double) = Stream(
        DoubleArray(size) { x[it] * k },
        DoubleArray(size) { y[it] * k },
        DoubleArray(size) { z[it] * k },
    )
}

enum class AccelUnit { AUTO, MPS2, G }
enum class GyroUnit { AUTO, RADPS, DEGPS }
enum class Sex { UNKNOWN, MALE, FEMALE }

/** Options that shape the preprocessing stage (resampling + band-pass). */
data class PreprocessOptions(
    val fsTarget: Double = 100.0,
    val accelUnit: AccelUnit = AccelUnit.AUTO,
    val gyroUnit: GyroUnit = GyroUnit.AUTO,
    val bandLowHz: Double = 0.5,
    val bandHighHz: Double = 4.0,
    val filterOrder: Int = 3,
)

/** Output of the preprocessing stage: uniform streams plus the step-ready signals. */
class Preprocessed(
    val options: PreprocessOptions,
    val fs: Double,
    val t: DoubleArray,
    val durationS: Double,
    val nRowsRaw: Int,
    val nativeRates: Map<SensorKind, Double>,
    val streams: Map<SensorKind, Stream>,
    /** |accel| including gravity (nominal when only linear acceleration exists). */
    val accMagRaw: DoubleArray,
    /** Gravity-free acceleration magnitude, unfiltered (median removed). */
    val aDyn: DoubleArray,
    /** Band-pass filtered dynamic acceleration - the main step-detection signal. */
    val aFilt: DoubleArray,
    /** Gyroscope magnitude in rad/s when a gyroscope stream exists. */
    val gyroMag: DoubleArray?,
    val dynamicSource: SensorKind,
    val accelUnitDetected: AccelUnit,
    val gyroUnitDetected: GyroUnit?,
) {
    val sensorsFound: List<SensorKind> get() = streams.keys.sortedBy { it.csvName }
    val n: Int get() = t.size
}

/** Where a step algorithm belongs conceptually. */
enum class AlgorithmFamily { PEAK, ZERO_CROSSING, FREQUENCY, THRESHOLD }

/** Unit of a tunable parameter (used by the UI for formatting only). */
enum class ParamUnit { SECONDS, HERTZ, FACTOR, MPS2, RADPS, SAMPLES, NONE }

/** Description of one tunable algorithm parameter, including its slider range. */
data class ParamSpec(
    val key: String,
    val default: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    val unit: ParamUnit,
)

/** Cadence estimate of one analysis window (frequency-domain algorithms). */
data class WindowCadence(val tSec: Double, val freqHz: Double, val steps: Double, val strength: Double = Double.NaN)

/** Machine-readable notes attached to results; the UI localises them. */
sealed class Note {
    object StrongPeriodicity : Note()
    data class IntervalCv(val cv: Double) : Note()
    data class GpxStepLength(val metres: Double) : Note()
    object OutOfPlausibleRange : Note()
    object FrequencyMethod : Note()
    data class RequiresSensor(val sensor: SensorKind) : Note()
    data class StrideLocked(val windows: Int) : Note()
}

/** Result of one step-counting algorithm. */
data class AlgorithmResult(
    val id: String,
    val family: AlgorithmFamily,
    val available: Boolean,
    val stepCount: Int,
    val stepIndices: IntArray,
    val cadenceSpm: Double,
    val params: Map<String, Double>,
    val windows: List<WindowCadence> = emptyList(),
    val notes: List<Note> = emptyList(),
    val confidence: Double = 0.0,
    val avgStepLengthGpxM: Double? = null,
    /** Alternative signal the algorithm worked on (gyro / vertical), for charting. */
    val signal: DoubleArray? = null,
) {
    val hasStepIndices: Boolean get() = stepIndices.isNotEmpty()
}

enum class StepLengthFamily { GPS, AMPLITUDE, BIOMECHANICAL }
enum class Requirement { GPX, ACCELEROMETER, ACCELEROMETER_AND_CALIBRATION, HEIGHT, HEIGHT_DEFAULT, NONE }

/** One step-length estimate. */
data class StepLengthMethod(
    val id: String,
    val family: StepLengthFamily,
    val stepLengthM: Double,
    val distanceM: Double?,
    val calibrated: Boolean,
    val requires: Requirement,
    val uncalibratedM: Double? = null,
    val gpxCalibratedM: Double? = null,
    val calibratedK: Double? = null,
    val defaultK: Double? = null,
    val coefficient: Double? = null,
)

data class StepLengthResult(
    val methods: List<StepLengthMethod>,
    val recommendedM: Double?,
    val recommendedSource: String,
    val referenceAlgorithmId: String,
    val referenceStepCount: Int,
    val heightUsedM: Double,
    val heightWasDefault: Boolean,
)

/** GPS track summary after cleaning implausible segments. */
data class GpxSummary(
    val hasTime: Boolean,
    val distanceM: Double,
    val rawDistanceM: Double,
    val durationSec: Double,
    val movingSpeedMps: Double,
    val nPoints: Int,
    val nRejected: Int,
    val elevationGainM: Double,
    val lat: DoubleArray,
    val lon: DoubleArray,
    val cumulativeDistanceM: DoubleArray,
    val timeSec: DoubleArray,
) {
    val movingSpeedKmh: Double get() = movingSpeedMps * 3.6
}

data class FileInfo(
    val sampleRateHz: Double,
    val durationSec: Double,
    val rows: Int,
    val sensorsFound: List<SensorKind>,
    val nativeRatesHz: Map<SensorKind, Double>,
    val dynamicSource: SensorKind,
)

data class Recommendation(
    val algorithmId: String,
    val stepCount: Int,
    val confidence: Double,
    val family: AlgorithmFamily,
    val gpxConsistent: Boolean,
)

data class SummaryStats(
    val minSteps: Int,
    val maxSteps: Int,
    val medianSteps: Int,
    val spread: Int,
    val globalPeriodicity: Double,
)

/** Cadence-over-time series (steps per minute) taken from the FFT windows. */
data class CadenceSeries(val tSec: DoubleArray, val spm: DoubleArray)

/** Everything the UI needs after a full analysis. */
data class AnalysisResult(
    val fileInfo: FileInfo,
    val gpx: GpxSummary?,
    val algorithms: List<AlgorithmResult>,
    val stepLength: StepLengthResult,
    val recommended: Recommendation,
    val summary: SummaryStats,
    val cadence: CadenceSeries,
    val referenceAlgorithmId: String,
) {
    fun algorithm(id: String): AlgorithmResult? = algorithms.firstOrNull { it.id == id }
}
