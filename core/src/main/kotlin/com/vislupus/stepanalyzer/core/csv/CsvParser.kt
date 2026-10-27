package com.vislupus.stepanalyzer.core.csv

import com.vislupus.stepanalyzer.core.config.CoreDefaults
import com.vislupus.stepanalyzer.core.model.RawData
import com.vislupus.stepanalyzer.core.model.RawStream
import com.vislupus.stepanalyzer.core.model.SensorKind
import com.vislupus.stepanalyzer.core.signal.DoubleList
import com.vislupus.stepanalyzer.core.signal.Stats
import java.io.BufferedReader
import java.io.Reader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Parses sensor CSV files in two layouts:
 *
 * LONG (one sample per row, a `sensor` column selects the stream) - the format the app records:
 *     timestamp_ms,sensor,x,y,z
 *     1782372767801,accel,-0.226,4.005,9.411
 *
 * WIDE (one row holds every stream):
 *     timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z
 *
 * Timestamps may be unix milliseconds, unix seconds, relative seconds or ISO-8601 strings.
 */
object CsvParser {

    class CsvFormatException(message: String) : Exception(message)

    fun parse(reader: Reader): RawData {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader, 1 shl 16)
        var headerLine = br.readLine() ?: throw CsvFormatException("CSV file is empty")
        if (headerLine.startsWith("﻿")) headerLine = headerLine.substring(1)
        val delimiter = detectDelimiter(headerLine)
        val header = headerLine.split(delimiter).map { it.trim().trim('"').lowercase() }
        val index = header.withIndex().associate { it.value to it.index }

        val sensorCol = index["sensor"] ?: index["type"] ?: index["name"]
        val hasXyz = index.containsKey("x") && index.containsKey("y") && index.containsKey("z")

        return if (sensorCol != null && hasXyz) {
            parseLong(br, delimiter, header, index, sensorCol)
        } else {
            parseWide(br, delimiter, header, index)
        }
    }

    private fun detectDelimiter(header: String): Char {
        val candidates = charArrayOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { c -> header.count { it == c } } ?: ','
    }

    private fun timeColumn(index: Map<String, Int>, order: List<String>): Int =
        order.firstNotNullOfOrNull { index[it] } ?: 0

    // ---------------------------------------------------------------- LONG format

    private class Collector {
        val t = DoubleList(4096)
        val x = DoubleList(4096)
        val y = DoubleList(4096)
        val z = DoubleList(4096)
    }

    private fun parseLong(
        br: BufferedReader, delimiter: Char, header: List<String>, index: Map<String, Int>, sensorCol: Int,
    ): RawData {
        val tCol = timeColumn(index, listOf("timestamp_ms", "timestamp", "time", "t", "ts", "time_ms"))
        val xCol = index.getValue("x")
        val yCol = index.getValue("y")
        val zCol = index.getValue("z")
        val maxCol = maxOf(tCol, sensorCol, xCol, yCol, zCol)

        val collectors = HashMap<SensorKind, Collector>()
        val aliasCache = HashMap<String, SensorKind?>()
        val fields = ArrayList<String>(header.size + 2)
        var rows = 0
        var isoTime = false
        var line = br.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                rows++
                splitInto(line, delimiter, fields)
                if (fields.size > maxCol) {
                    val kind = aliasCache.getOrPut(fields[sensorCol]) { SensorKind.fromAlias(fields[sensorCol]) }
                    if (kind != null) {
                        val tv = parseTime(fields[tCol])
                        if (tv.isNaN() && !isoTime && fields[tCol].contains('T')) isoTime = true
                        val xv = fields[xCol].toDoubleOrNull()
                        val yv = fields[yCol].toDoubleOrNull()
                        val zv = fields[zCol].toDoubleOrNull()
                        if (!tv.isNaN() && xv != null && yv != null && zv != null &&
                            xv.isFinite() && yv.isFinite() && zv.isFinite()
                        ) {
                            val c = collectors.getOrPut(kind) { Collector() }
                            c.t.add(tv); c.x.add(xv); c.y.add(yv); c.z.add(zv)
                        }
                    }
                }
            }
            line = br.readLine()
        }
        return finish(rows, collectors)
    }

    // ---------------------------------------------------------------- WIDE format

    private fun parseWide(br: BufferedReader, delimiter: Char, header: List<String>, index: Map<String, Int>): RawData {
        val tCol = timeColumn(index, listOf("timestamp", "time", "t", "ts", "timestamp_ms"))
        val prefixes = mapOf(
            SensorKind.ACCEL to listOf("acc_", "accel_", "a_", "acceleration_", "ax|ay|az"),
            SensorKind.UACCEL to listOf("uacc_", "linacc_", "linear_acc_", "lin_acc_", "ua_"),
            SensorKind.GYRO to listOf("gyro_", "gyr_", "g_", "gyroscope_", "gx|gy|gz"),
            SensorKind.MAG to listOf("mag_", "magnetometer_", "m_", "magnetic_", "mx|my|mz"),
        )
        val columns = HashMap<SensorKind, IntArray>()
        for ((kind, prefs) in prefixes) {
            for (p in prefs) {
                val names = if (p.contains('|')) p.split('|') else listOf(p + "x", p + "y", p + "z")
                val cols = names.map { index[it] }
                if (cols.all { it != null }) {
                    columns[kind] = IntArray(3) { cols[it]!! }
                    break
                }
            }
        }
        if (columns.isEmpty()) {
            throw CsvFormatException(
                "No accelerometer data found. Expected a long-format CSV with a 'sensor' column " +
                    "(accel/uaccel/gyro/mag) and x,y,z columns, or a wide-format CSV with acc_x/acc_y/acc_z columns."
            )
        }
        val collectors = HashMap<SensorKind, Collector>()
        val fields = ArrayList<String>(header.size + 2)
        var rows = 0
        var line = br.readLine()
        while (line != null) {
            if (line.isNotBlank()) {
                rows++
                splitInto(line, delimiter, fields)
                if (fields.size > tCol) {
                    val tv = parseTime(fields[tCol])
                    if (!tv.isNaN()) {
                        for ((kind, cols) in columns) {
                            if (fields.size <= cols.max()) continue
                            val xv = fields[cols[0]].toDoubleOrNull()
                            val yv = fields[cols[1]].toDoubleOrNull()
                            val zv = fields[cols[2]].toDoubleOrNull()
                            if (xv != null && yv != null && zv != null && xv.isFinite() && yv.isFinite() && zv.isFinite()) {
                                val c = collectors.getOrPut(kind) { Collector() }
                                c.t.add(tv); c.x.add(xv); c.y.add(yv); c.z.add(zv)
                            }
                        }
                    }
                }
            }
            line = br.readLine()
        }
        return finish(rows, collectors)
    }

    // ---------------------------------------------------------------- shared

    private fun finish(rows: Int, collectors: Map<SensorKind, Collector>): RawData {
        if (!collectors.containsKey(SensorKind.ACCEL) && !collectors.containsKey(SensorKind.UACCEL)) {
            throw CsvFormatException(
                "No accelerometer data found. Expected a long-format CSV with a 'sensor' column " +
                    "(accel/uaccel/gyro/mag) and x,y,z columns, or a wide-format CSV with acc_x/acc_y/acc_z columns."
            )
        }
        // Normalise timestamps to seconds from the first sample (shared across streams).
        val allT = DoubleList(collectors.values.sumOf { it.t.size })
        for (c in collectors.values) for (i in 0 until c.t.size) allT.add(c.t[i])
        val allArr = allT.toArray()
        val scale = timeScale(Stats.median(allArr))
        var tMin = Double.POSITIVE_INFINITY
        for (v in allArr) if (v * scale < tMin) tMin = v * scale

        val streams = HashMap<SensorKind, RawStream>()
        for ((kind, c) in collectors) {
            if (c.t.size < CoreDefaults.Preprocess.MIN_SAMPLES) continue
            val t = DoubleArray(c.t.size) { c.t[it] * scale - tMin }
            streams[kind] = RawStream(t, c.x.toArray(), c.y.toArray(), c.z.toArray())
        }
        return RawData(rows, streams)
    }

    /** Heuristic from the old analyser: unix ms (~1.7e12) / unix s (~1.7e9) / relative ms / s. */
    private fun timeScale(median: Double): Double = when {
        median > 1e11 -> 1e-3
        median > 1e9 -> 1.0
        median > 1e6 -> 1e-3
        else -> 1.0
    }

    /** Parse a numeric or ISO-8601 timestamp into a raw number (unix seconds for ISO). */
    private fun parseTime(field: String): Double {
        val s = field.trim().trim('"')
        s.toDoubleOrNull()?.let { return it }
        if (s.isEmpty()) return Double.NaN
        return try {
            OffsetDateTime.parse(s).toInstant().let { it.epochSecond + it.nano / 1e9 }
        } catch (e: Exception) {
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).let { it.epochSecond + it.nano / 1e9 }
            } catch (e2: Exception) {
                Double.NaN
            }
        }
    }

    /** Allocation-light split that reuses the output list. */
    private fun splitInto(line: String, delimiter: Char, out: ArrayList<String>) {
        out.clear()
        var start = 0
        while (true) {
            val idx = line.indexOf(delimiter, start)
            if (idx < 0) {
                out.add(line.substring(start).trim())
                break
            }
            out.add(line.substring(start, idx).trim())
            start = idx + 1
        }
    }
}
