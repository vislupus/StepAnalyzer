package com.vislupus.stepanalyzer.util

import com.vislupus.stepanalyzer.config.AppConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Number/date formatting shared by all screens. */
object Formatters {

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun durationSeconds(seconds: Double): String = duration((seconds * 1000).toLong())

    fun distance(m: Double): String = if (m >= 1000) String.format(Locale.US, "%.2f km", m / 1000) else String.format(Locale.US, "%.0f m", m)

    fun metres(m: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f m", m)

    fun number(v: Double, decimals: Int = 2): String = String.format(Locale.US, "%.${decimals}f", v)

    fun percent(v: Double): String = "${(v * 100).roundToInt()}%"

    fun date(epochMs: Long, pattern: String = AppConfig.Storage.DATE_PATTERN): String =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    fun fileSize(bytes: Long): String = when {
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** Format a parameter value with a sensible number of decimals for its slider step. */
    fun param(value: Double, step: Double): String {
        val decimals = when {
            step >= 1.0 -> 0
            step >= 0.1 -> 1
            step >= 0.01 -> 2
            else -> 3
        }
        return String.format(Locale.US, "%.${decimals}f", value)
    }
}
