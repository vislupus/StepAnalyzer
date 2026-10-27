package com.vislupus.stepanalyzer.data.repo

import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.core.model.AnalysisResult
import com.vislupus.stepanalyzer.core.model.PreprocessOptions
import com.vislupus.stepanalyzer.data.db.AlgorithmResultRow
import com.vislupus.stepanalyzer.data.db.AnalysisRun
import com.vislupus.stepanalyzer.data.db.AppDatabase
import com.vislupus.stepanalyzer.data.db.GpsPoint
import com.vislupus.stepanalyzer.data.db.Session
import com.vislupus.stepanalyzer.data.db.StepLengthRow
import com.vislupus.stepanalyzer.data.files.GpxWriter
import com.vislupus.stepanalyzer.data.files.SessionFiles
import com.vislupus.stepanalyzer.util.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** Sessions, their files, GPS points and stored analysis runs. All methods are blocking (call from IO). */
class SessionRepository(private val db: AppDatabase, val files: SessionFiles) {

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    /** Live list for the Sessions screen; refreshed after every change. */
    val sessions: StateFlow<List<Session>> get() = _sessions

    fun refresh() {
        _sessions.value = db.allSessions()
    }

    fun get(id: Long): Session? = db.getSession(id)

    fun insert(session: Session): Long {
        val id = db.insertSession(session)
        refresh()
        return id
    }

    fun update(session: Session) {
        db.updateSession(session)
        refresh()
    }

    fun rename(id: Long, name: String) {
        db.getSession(id)?.let { db.updateSession(it.copy(name = name)) }
        refresh()
    }

    fun delete(id: Long) {
        db.deleteSession(id)
        files.deleteSessionFiles(id)
        refresh()
    }

    fun deleteAll() {
        for (s in db.allSessions()) files.deleteSessionFiles(s.id)
        db.clearAll()
        refresh()
    }

    // ---------------------------------------------------------------- import

    /** Create a session from an external CSV (and optionally GPX) stream. */
    fun importCsv(name: String, csv: InputStream, gpx: InputStream?): Long {
        val now = System.currentTimeMillis()
        val id = db.insertSession(Session(name = name, source = Session.SOURCE_IMPORTED, createdAt = now))
        val csvFile = files.csvFile(id)
        val bytes = files.importInto(csvFile, csv)
        var gpxPath: String? = null
        if (gpx != null) {
            val gpxFile = files.gpxFile(id)
            files.importInto(gpxFile, gpx)
            gpxPath = gpxFile.absolutePath
        }
        val session = db.getSession(id)!!.copy(csvPath = csvFile.absolutePath, gpxPath = gpxPath, fileBytes = bytes)
        db.updateSession(session)
        refresh()
        return id
    }

    /** Attach (or replace) the GPX file of an existing session. */
    fun attachGpx(sessionId: Long, gpx: InputStream) {
        val gpxFile = files.gpxFile(sessionId)
        files.importInto(gpxFile, gpx)
        db.getSession(sessionId)?.let { db.updateSession(it.copy(gpxPath = gpxFile.absolutePath)) }
        refresh()
    }

    fun removeGpx(sessionId: Long) {
        db.getSession(sessionId)?.let { s ->
            s.gpxPath?.let { File(it).delete() }
            db.updateSession(s.copy(gpxPath = null))
        }
        refresh()
    }

    // ---------------------------------------------------------------- gps

    fun gpsPoints(sessionId: Long): List<GpsPoint> = db.gpsPoints(sessionId)

    fun saveGpsPoints(points: List<GpsPoint>) {
        if (AppConfig.Features.STORE_GPS_POINTS_IN_DB) db.insertGpsPoints(points)
    }

    /** Re-export the GPX of a session into the export directory (with analysis extensions). */
    fun exportGpx(session: Session, extensions: Map<String, String>): File? {
        val points = db.gpsPoints(session.id)
        if (points.isEmpty()) return session.gpxPath?.let { File(it) }?.takeIf { it.exists() }
        val out = File(files.exportDir, "${safeFileName(session.name)}.gpx")
        GpxWriter.write(out, session.name, points, extensions)
        return out
    }

    fun exportCsv(session: Session): File? {
        val src = session.csvPath?.let { File(it) }?.takeIf { it.exists() } ?: return null
        val out = File(files.exportDir, "${safeFileName(session.name)}.csv")
        src.copyTo(out, overwrite = true)
        return out
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9А-Яа-я._ -]"), "_").trim().ifEmpty { "session" }

    // ---------------------------------------------------------------- analysis runs

    fun runs(sessionId: Long): List<AnalysisRun> = db.runsForSession(sessionId)

    fun runCount(): Int = db.runCount()

    /** Persist a full analysis result and refresh the session's summary columns. */
    fun saveRun(
        session: Session,
        result: AnalysisResult,
        params: Map<String, Map<String, Double>>,
        options: PreprocessOptions,
    ): Long {
        val now = System.currentTimeMillis()
        val optionsJson = JSONObject().apply {
            put("fs_target", options.fsTarget)
            put("band_low_hz", options.bandLowHz)
            put("band_high_hz", options.bandHighHz)
            put("accel_unit", options.accelUnit.name)
            put("gyro_unit", options.gyroUnit.name)
            put("filter_order", options.filterOrder)
        }.toString()
        val summaryJson = JSONObject().apply {
            put("min_steps", result.summary.minSteps)
            put("max_steps", result.summary.maxSteps)
            put("median_steps", result.summary.medianSteps)
            put("spread", result.summary.spread)
            put("global_periodicity", result.summary.globalPeriodicity)
            put("duration_sec", result.fileInfo.durationSec)
            put("sample_rate_hz", result.fileInfo.sampleRateHz)
            put("rows", result.fileInfo.rows)
        }.toString()
        val run = AnalysisRun(
            sessionId = session.id,
            createdAt = now,
            paramsJson = Json.paramsToJson(params),
            optionsJson = optionsJson,
            recommendedAlgorithm = result.recommended.algorithmId,
            recommendedSteps = result.recommended.stepCount,
            recommendedConfidence = result.recommended.confidence,
            referenceAlgorithm = result.referenceAlgorithmId,
            stepLengthM = result.stepLength.recommendedM,
            gpxDistanceM = result.gpx?.distanceM,
            summaryJson = summaryJson,
        )
        val algoRows = result.algorithms.map { a ->
            AlgorithmResultRow(
                runId = 0,
                algorithmId = a.id,
                available = a.available,
                stepCount = a.stepCount,
                cadenceSpm = a.cadenceSpm,
                confidence = a.confidence,
                paramsJson = Json.doubleMapToJson(a.params),
                stepIndices = if (AppConfig.Features.STORE_STEP_INDICES_IN_DB) a.stepIndices.joinToString(",") else "",
                notesJson = Json.stringListToJson(a.notes.map { it.toString() }),
            )
        }
        val methodRows = result.stepLength.methods.map { m ->
            StepLengthRow(
                runId = 0,
                methodId = m.id,
                stepLengthM = m.stepLengthM,
                distanceM = m.distanceM,
                calibrated = m.calibrated,
                extraJson = JSONObject().apply {
                    m.calibratedK?.let { put("calibrated_k", it) }
                    m.defaultK?.let { put("default_k", it) }
                    m.uncalibratedM?.let { put("uncalibrated_m", it) }
                    m.coefficient?.let { put("coefficient", it) }
                }.toString(),
            )
        }
        val runId = db.insertRun(run, algoRows, methodRows)
        db.updateSession(
            session.copy(
                lastSteps = result.recommended.stepCount,
                lastAlgorithmId = result.recommended.algorithmId,
                lastConfidence = result.recommended.confidence,
                lastStepLengthM = result.stepLength.recommendedM,
                lastAnalyzedAt = now,
            )
        )
        refresh()
        return runId
    }

    fun deleteRun(runId: Long) = db.deleteRun(runId)

    fun storageBytes(): Long = files.storageBytes(db.file)
}
