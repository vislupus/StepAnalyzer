package com.vislupus.stepanalyzer.data.db

/** A recording (or imported file set). Mirrors the `sessions` table. */
data class Session(
    val id: Long = 0,
    val name: String,
    /** "recorded" or "imported". */
    val source: String,
    val createdAt: Long,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val durationMs: Long = 0,
    val csvPath: String? = null,
    val gpxPath: String? = null,
    val accelCount: Int = 0,
    val uaccelCount: Int = 0,
    val gyroCount: Int = 0,
    val magCount: Int = 0,
    val gpsCount: Int = 0,
    val gpsDistanceM: Double = 0.0,
    /** Steps reported by the phone's own TYPE_STEP_COUNTER during the recording (-1 = n/a). */
    val hwStepCounter: Int = -1,
    /** Events from TYPE_STEP_DETECTOR during the recording (-1 = n/a). */
    val hwStepDetector: Int = -1,
    /** Steps counted by the simple live detector while recording. */
    val liveSteps: Int = 0,
    val notes: String = "",
    val sensorPeriodUs: Int = 0,
    val fileBytes: Long = 0,
    // denormalised summary of the latest analysis run (for the list screen)
    val lastSteps: Int? = null,
    val lastAlgorithmId: String? = null,
    val lastConfidence: Double? = null,
    val lastStepLengthM: Double? = null,
    val lastAnalyzedAt: Long? = null,
) {
    val isComplete: Boolean get() = endedAt > 0 || source == SOURCE_IMPORTED
    val hasGpx: Boolean get() = !gpxPath.isNullOrEmpty()

    companion object {
        const val SOURCE_RECORDED = "recorded"
        const val SOURCE_IMPORTED = "imported"
    }
}

data class GpsPoint(
    val sessionId: Long,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val ele: Double,
    val accuracyM: Float,
    val speedMps: Float,
)

/** One stored analysis run (parameters + headline results). */
data class AnalysisRun(
    val id: Long = 0,
    val sessionId: Long,
    val createdAt: Long,
    val paramsJson: String,
    val optionsJson: String,
    val recommendedAlgorithm: String,
    val recommendedSteps: Int,
    val recommendedConfidence: Double,
    val referenceAlgorithm: String,
    val stepLengthM: Double?,
    val gpxDistanceM: Double?,
    val summaryJson: String,
)

data class AlgorithmResultRow(
    val runId: Long,
    val algorithmId: String,
    val available: Boolean,
    val stepCount: Int,
    val cadenceSpm: Double,
    val confidence: Double,
    val paramsJson: String,
    val stepIndices: String,
    val notesJson: String,
)

data class StepLengthRow(
    val runId: Long,
    val methodId: String,
    val stepLengthM: Double,
    val distanceM: Double?,
    val calibrated: Boolean,
    val extraJson: String,
)
