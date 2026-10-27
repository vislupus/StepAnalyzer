package com.vislupus.stepanalyzer.data.files

import android.content.Context
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.data.db.GpsPoint
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * All files live inside the app sandbox: <filesDir>/sessions/<sessionId>/{sensors.csv,track.gpx}.
 * Nothing is written to shared storage.
 */
class SessionFiles(context: Context) {
    val root: File = context.filesDir
    val sessionsDir: File = File(root, AppConfig.Storage.SESSIONS_DIR).apply { mkdirs() }
    val exportDir: File = File(root, AppConfig.Storage.EXPORT_DIR).apply { mkdirs() }

    fun sessionDir(sessionId: Long): File = File(sessionsDir, sessionId.toString()).apply { mkdirs() }
    fun csvFile(sessionId: Long): File = File(sessionDir(sessionId), AppConfig.Storage.CSV_FILE)
    fun gpxFile(sessionId: Long): File = File(sessionDir(sessionId), AppConfig.Storage.GPX_FILE)

    fun deleteSessionFiles(sessionId: Long) {
        File(sessionsDir, sessionId.toString()).deleteRecursively()
    }

    /** Copy an external stream (SAF import) into the session folder. Returns the file size. */
    fun importInto(target: File, input: InputStream): Long {
        target.parentFile?.mkdirs()
        var total = 0L
        target.outputStream().buffered(1 shl 16).use { out ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
        }
        return total
    }

    /** Bytes used by all session folders plus the database. */
    fun storageBytes(dbFile: File): Long {
        var total = if (dbFile.exists()) dbFile.length() else 0L
        sessionsDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    fun clearExports() {
        exportDir.listFiles()?.forEach { it.delete() }
    }
}

/** Streams sensor samples to a CSV file in the long format the analyser reads. */
class CsvSampleWriter(file: File) : AutoCloseable {
    private val writer = BufferedWriter(FileWriter(file, false), AppConfig.Recording.CSV_BUFFER_BYTES)
    private val sb = StringBuilder(96)
    var rows = 0L
        private set

    init {
        writer.write("timestamp_ms,sensor,x,y,z\n")
    }

    fun append(timestampMs: Long, sensor: String, x: Float, y: Float, z: Float) {
        sb.setLength(0)
        sb.append(timestampMs).append(',').append(sensor).append(',')
            .append(x).append(',').append(y).append(',').append(z).append('\n')
        writer.write(sb.toString())
        rows++
    }

    fun flush() = writer.flush()

    override fun close() {
        writer.flush()
        writer.close()
    }
}

/** Writes a GPX 1.1 track with optional summary extensions. */
object GpxWriter {
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)

    fun write(
        file: File,
        name: String,
        points: List<GpsPoint>,
        extensions: Map<String, String> = emptyMap(),
    ) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { w ->
            val safeName = escape(name)
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            w.write("<gpx version=\"1.1\" creator=\"${AppConfig.Storage.GPX_CREATOR}\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            w.write("  <metadata>\n    <name>$safeName</name>\n")
            if (extensions.isNotEmpty()) {
                w.write("    <desc>" + extensions.entries.joinToString(", ") { "${it.key}=${escape(it.value)}" } + "</desc>\n")
            }
            if (points.isNotEmpty()) w.write("    <time>${isoFormatter.format(Instant.ofEpochMilli(points.first().timeMs))}</time>\n")
            w.write("  </metadata>\n  <trk>\n    <name>$safeName</name>\n")
            if (extensions.isNotEmpty()) {
                w.write("    <extensions>\n")
                for ((k, v) in extensions) w.write("      <$k>${escape(v)}</$k>\n")
                w.write("    </extensions>\n")
            }
            w.write("    <trkseg>\n")
            for (p in points) {
                w.write("      <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">\n")
                w.write("        <time>${isoFormatter.format(Instant.ofEpochMilli(p.timeMs))}</time>\n")
                if (!p.ele.isNaN()) w.write("        <ele>${p.ele}</ele>\n")
                w.write("      </trkpt>\n")
            }
            w.write("    </trkseg>\n  </trk>\n</gpx>\n")
        }
    }

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
