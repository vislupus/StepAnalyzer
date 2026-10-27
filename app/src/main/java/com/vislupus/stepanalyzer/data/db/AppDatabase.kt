package com.vislupus.stepanalyzer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.vislupus.stepanalyzer.config.AppConfig
import java.io.File

/**
 * Plain SQLite (no ORM) so that everything the app stores is visible in one file:
 * <filesDir>/stepanalyzer.db. Tables:
 *
 *  settings           key/value user settings
 *  sessions           recordings and imports (one row each)
 *  gps_points         GPS fixes of a session
 *  analysis_runs      one row per analysis (parameters + headline result)
 *  algorithm_results  per-algorithm result rows of a run
 *  step_length_results per-method step-length rows of a run
 */
class AppDatabase(context: Context) : SQLiteOpenHelper(
    context,
    File(context.filesDir, AppConfig.Storage.DATABASE_FILE).absolutePath,
    null,
    VERSION,
) {
    val file: File = File(context.filesDir, AppConfig.Storage.DATABASE_FILE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE settings (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                started_at INTEGER NOT NULL DEFAULT 0,
                ended_at INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                csv_path TEXT,
                gpx_path TEXT,
                accel_count INTEGER NOT NULL DEFAULT 0,
                uaccel_count INTEGER NOT NULL DEFAULT 0,
                gyro_count INTEGER NOT NULL DEFAULT 0,
                mag_count INTEGER NOT NULL DEFAULT 0,
                gps_count INTEGER NOT NULL DEFAULT 0,
                gps_distance_m REAL NOT NULL DEFAULT 0,
                hw_step_counter INTEGER NOT NULL DEFAULT -1,
                hw_step_detector INTEGER NOT NULL DEFAULT -1,
                live_steps INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                sensor_period_us INTEGER NOT NULL DEFAULT 0,
                file_bytes INTEGER NOT NULL DEFAULT 0,
                last_steps INTEGER,
                last_algorithm_id TEXT,
                last_confidence REAL,
                last_step_length_m REAL,
                last_analyzed_at INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE gps_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                time_ms INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                ele REAL,
                accuracy_m REAL,
                speed_mps REAL)"""
        )
        db.execSQL("CREATE INDEX idx_gps_session ON gps_points(session_id)")
        db.execSQL(
            """CREATE TABLE analysis_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                params_json TEXT NOT NULL,
                options_json TEXT NOT NULL,
                recommended_algorithm TEXT NOT NULL,
                recommended_steps INTEGER NOT NULL,
                recommended_confidence REAL NOT NULL,
                reference_algorithm TEXT NOT NULL,
                step_length_m REAL,
                gpx_distance_m REAL,
                summary_json TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_runs_session ON analysis_runs(session_id)")
        db.execSQL(
            """CREATE TABLE algorithm_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL REFERENCES analysis_runs(id) ON DELETE CASCADE,
                algorithm_id TEXT NOT NULL,
                available INTEGER NOT NULL,
                step_count INTEGER NOT NULL,
                cadence_spm REAL NOT NULL,
                confidence REAL NOT NULL,
                params_json TEXT NOT NULL,
                step_indices TEXT NOT NULL,
                notes_json TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_algo_run ON algorithm_results(run_id)")
        db.execSQL(
            """CREATE TABLE step_length_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id INTEGER NOT NULL REFERENCES analysis_runs(id) ON DELETE CASCADE,
                method_id TEXT NOT NULL,
                step_length_m REAL NOT NULL,
                distance_m REAL,
                calibrated INTEGER NOT NULL,
                extra_json TEXT NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_sl_run ON step_length_results(run_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First schema version: nothing to migrate yet. Add ALTER TABLE statements here later.
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    // ------------------------------------------------------------------ settings

    fun getSetting(key: String): String? = readableDatabase.query(
        "settings", arrayOf("value"), "key = ?", arrayOf(key), null, null, null,
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun allSettings(): Map<String, String> = readableDatabase.query(
        "settings", arrayOf("key", "value"), null, null, null, null, null,
    ).use { c ->
        val out = HashMap<String, String>()
        while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        out
    }

    fun putSetting(key: String, value: String) {
        val cv = ContentValues(2)
        cv.put("key", key)
        cv.put("value", value)
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun putSettings(values: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((k, v) in values) {
                val cv = ContentValues(2)
                cv.put("key", k)
                cv.put("value", v)
                db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------ sessions

    private fun Session.toValues(): ContentValues = ContentValues().apply {
        put("name", name)
        put("source", source)
        put("created_at", createdAt)
        put("started_at", startedAt)
        put("ended_at", endedAt)
        put("duration_ms", durationMs)
        put("csv_path", csvPath)
        put("gpx_path", gpxPath)
        put("accel_count", accelCount)
        put("uaccel_count", uaccelCount)
        put("gyro_count", gyroCount)
        put("mag_count", magCount)
        put("gps_count", gpsCount)
        put("gps_distance_m", gpsDistanceM)
        put("hw_step_counter", hwStepCounter)
        put("hw_step_detector", hwStepDetector)
        put("live_steps", liveSteps)
        put("notes", notes)
        put("sensor_period_us", sensorPeriodUs)
        put("file_bytes", fileBytes)
        if (lastSteps != null) put("last_steps", lastSteps) else putNull("last_steps")
        put("last_algorithm_id", lastAlgorithmId)
        if (lastConfidence != null) put("last_confidence", lastConfidence) else putNull("last_confidence")
        if (lastStepLengthM != null) put("last_step_length_m", lastStepLengthM) else putNull("last_step_length_m")
        if (lastAnalyzedAt != null) put("last_analyzed_at", lastAnalyzedAt) else putNull("last_analyzed_at")
    }

    private fun Cursor.toSession(): Session = Session(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        source = getString(getColumnIndexOrThrow("source")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        startedAt = getLong(getColumnIndexOrThrow("started_at")),
        endedAt = getLong(getColumnIndexOrThrow("ended_at")),
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        csvPath = getStringOrNull("csv_path"),
        gpxPath = getStringOrNull("gpx_path"),
        accelCount = getInt(getColumnIndexOrThrow("accel_count")),
        uaccelCount = getInt(getColumnIndexOrThrow("uaccel_count")),
        gyroCount = getInt(getColumnIndexOrThrow("gyro_count")),
        magCount = getInt(getColumnIndexOrThrow("mag_count")),
        gpsCount = getInt(getColumnIndexOrThrow("gps_count")),
        gpsDistanceM = getDouble(getColumnIndexOrThrow("gps_distance_m")),
        hwStepCounter = getInt(getColumnIndexOrThrow("hw_step_counter")),
        hwStepDetector = getInt(getColumnIndexOrThrow("hw_step_detector")),
        liveSteps = getInt(getColumnIndexOrThrow("live_steps")),
        notes = getString(getColumnIndexOrThrow("notes")),
        sensorPeriodUs = getInt(getColumnIndexOrThrow("sensor_period_us")),
        fileBytes = getLong(getColumnIndexOrThrow("file_bytes")),
        lastSteps = getIntOrNull("last_steps"),
        lastAlgorithmId = getStringOrNull("last_algorithm_id"),
        lastConfidence = getDoubleOrNull("last_confidence"),
        lastStepLengthM = getDoubleOrNull("last_step_length_m"),
        lastAnalyzedAt = getLongOrNull("last_analyzed_at"),
    )

    fun insertSession(session: Session): Long = writableDatabase.insertOrThrow("sessions", null, session.toValues())

    fun updateSession(session: Session) {
        writableDatabase.update("sessions", session.toValues(), "id = ?", arrayOf(session.id.toString()))
    }

    fun deleteSession(id: Long) {
        writableDatabase.delete("sessions", "id = ?", arrayOf(id.toString()))
    }

    fun getSession(id: Long): Session? = readableDatabase.query(
        "sessions", null, "id = ?", arrayOf(id.toString()), null, null, null,
    ).use { if (it.moveToFirst()) it.toSession() else null }

    fun allSessions(): List<Session> = readableDatabase.query(
        "sessions", null, null, null, null, null, "created_at DESC",
    ).use { c ->
        val out = ArrayList<Session>(c.count)
        while (c.moveToNext()) out.add(c.toSession())
        out
    }

    // ------------------------------------------------------------------ gps points

    fun insertGpsPoints(points: List<GpsPoint>) {
        if (points.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO gps_points(session_id, time_ms, lat, lon, ele, accuracy_m, speed_mps) VALUES (?,?,?,?,?,?,?)"
            )
            for (p in points) {
                stmt.clearBindings()
                stmt.bindLong(1, p.sessionId)
                stmt.bindLong(2, p.timeMs)
                stmt.bindDouble(3, p.lat)
                stmt.bindDouble(4, p.lon)
                if (p.ele.isNaN()) stmt.bindNull(5) else stmt.bindDouble(5, p.ele)
                stmt.bindDouble(6, p.accuracyM.toDouble())
                stmt.bindDouble(7, p.speedMps.toDouble())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun gpsPoints(sessionId: Long): List<GpsPoint> = readableDatabase.query(
        "gps_points", null, "session_id = ?", arrayOf(sessionId.toString()), null, null, "time_ms ASC",
    ).use { c ->
        val out = ArrayList<GpsPoint>(c.count)
        while (c.moveToNext()) {
            out.add(
                GpsPoint(
                    sessionId = sessionId,
                    timeMs = c.getLong(c.getColumnIndexOrThrow("time_ms")),
                    lat = c.getDouble(c.getColumnIndexOrThrow("lat")),
                    lon = c.getDouble(c.getColumnIndexOrThrow("lon")),
                    ele = c.getDoubleOrNull("ele") ?: Double.NaN,
                    accuracyM = (c.getDoubleOrNull("accuracy_m") ?: 0.0).toFloat(),
                    speedMps = (c.getDoubleOrNull("speed_mps") ?: 0.0).toFloat(),
                )
            )
        }
        out
    }

    fun deleteGpsPoints(sessionId: Long) {
        writableDatabase.delete("gps_points", "session_id = ?", arrayOf(sessionId.toString()))
    }

    // ------------------------------------------------------------------ analysis runs

    fun insertRun(run: AnalysisRun, algorithms: List<AlgorithmResultRow>, methods: List<StepLengthRow>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("session_id", run.sessionId)
                put("created_at", run.createdAt)
                put("params_json", run.paramsJson)
                put("options_json", run.optionsJson)
                put("recommended_algorithm", run.recommendedAlgorithm)
                put("recommended_steps", run.recommendedSteps)
                put("recommended_confidence", run.recommendedConfidence)
                put("reference_algorithm", run.referenceAlgorithm)
                if (run.stepLengthM != null) put("step_length_m", run.stepLengthM) else putNull("step_length_m")
                if (run.gpxDistanceM != null) put("gpx_distance_m", run.gpxDistanceM) else putNull("gpx_distance_m")
                put("summary_json", run.summaryJson)
            }
            val runId = db.insertOrThrow("analysis_runs", null, cv)
            for (a in algorithms) {
                db.insertOrThrow(
                    "algorithm_results", null,
                    ContentValues().apply {
                        put("run_id", runId)
                        put("algorithm_id", a.algorithmId)
                        put("available", if (a.available) 1 else 0)
                        put("step_count", a.stepCount)
                        put("cadence_spm", a.cadenceSpm)
                        put("confidence", a.confidence)
                        put("params_json", a.paramsJson)
                        put("step_indices", a.stepIndices)
                        put("notes_json", a.notesJson)
                    },
                )
            }
            for (m in methods) {
                db.insertOrThrow(
                    "step_length_results", null,
                    ContentValues().apply {
                        put("run_id", runId)
                        put("method_id", m.methodId)
                        put("step_length_m", m.stepLengthM)
                        if (m.distanceM != null) put("distance_m", m.distanceM) else putNull("distance_m")
                        put("calibrated", if (m.calibrated) 1 else 0)
                        put("extra_json", m.extraJson)
                    },
                )
            }
            db.setTransactionSuccessful()
            return runId
        } finally {
            db.endTransaction()
        }
    }

    fun runsForSession(sessionId: Long): List<AnalysisRun> = readableDatabase.query(
        "analysis_runs", null, "session_id = ?", arrayOf(sessionId.toString()), null, null, "created_at DESC",
    ).use { c ->
        val out = ArrayList<AnalysisRun>(c.count)
        while (c.moveToNext()) {
            out.add(
                AnalysisRun(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    sessionId = sessionId,
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    paramsJson = c.getString(c.getColumnIndexOrThrow("params_json")),
                    optionsJson = c.getString(c.getColumnIndexOrThrow("options_json")),
                    recommendedAlgorithm = c.getString(c.getColumnIndexOrThrow("recommended_algorithm")),
                    recommendedSteps = c.getInt(c.getColumnIndexOrThrow("recommended_steps")),
                    recommendedConfidence = c.getDouble(c.getColumnIndexOrThrow("recommended_confidence")),
                    referenceAlgorithm = c.getString(c.getColumnIndexOrThrow("reference_algorithm")),
                    stepLengthM = c.getDoubleOrNull("step_length_m"),
                    gpxDistanceM = c.getDoubleOrNull("gpx_distance_m"),
                    summaryJson = c.getString(c.getColumnIndexOrThrow("summary_json")),
                )
            )
        }
        out
    }

    fun runCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM analysis_runs", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    fun deleteRun(runId: Long) {
        writableDatabase.delete("analysis_runs", "id = ?", arrayOf(runId.toString()))
    }

    /** Remove every row of every table (settings included). */
    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in listOf("step_length_results", "algorithm_results", "analysis_runs", "gps_points", "sessions", "settings")) {
                db.delete(t, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val VERSION = 1
    }
}

private fun Cursor.getStringOrNull(column: String): String? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getString(i)
}

private fun Cursor.getIntOrNull(column: String): Int? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getInt(i)
}

private fun Cursor.getLongOrNull(column: String): Long? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getLong(i)
}

private fun Cursor.getDoubleOrNull(column: String): Double? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getDouble(i)
}
