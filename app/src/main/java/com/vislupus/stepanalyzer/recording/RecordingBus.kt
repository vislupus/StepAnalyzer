package com.vislupus.stepanalyzer.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RecordingStatus { IDLE, RECORDING, STOPPING }

/** Snapshot of the recording service published to the UI several times per second. */
data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val sessionId: Long = 0,
    val startedAtMs: Long = 0,
    val elapsedMs: Long = 0,
    /** Samples written per sensor (CSV name -> count). */
    val counts: Map<String, Int> = emptyMap(),
    /** Measured accelerometer rate over the last second. */
    val accelRateHz: Double = 0.0,
    val gpsEnabled: Boolean = false,
    val gpsPoints: Int = 0,
    val gpsDistanceM: Double = 0.0,
    val gpsAccuracyM: Float = Float.NaN,
    val gpsHasFix: Boolean = false,
    val liveSteps: Int = 0,
    /** -1 when the sensor is missing or permission was denied. */
    val hwStepCounter: Int = -1,
    val hwStepDetector: Int = -1,
    /** Recent gravity-free acceleration magnitude for the live chart (oldest first). */
    val liveSignal: FloatArray = FloatArray(0),
    val availableSensors: Set<String> = emptySet(),
    /** Set once after a recording has been saved so the UI can offer to open it. */
    val lastSavedSessionId: Long? = null,
    val error: String? = null,
) {
    val isRecording: Boolean get() = status == RecordingStatus.RECORDING
}

/** Process-wide bridge between the foreground service and the Compose UI. */
object RecordingBus {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> get() = _state

    fun publish(state: RecordingState) {
        _state.value = state
    }

    fun update(transform: (RecordingState) -> RecordingState) {
        _state.value = transform(_state.value)
    }

    /** Clear the "saved" marker once the UI has reacted to it. */
    fun consumeSaved() = update { it.copy(lastSavedSessionId = null) }

    fun consumeError() = update { it.copy(error = null) }
}
