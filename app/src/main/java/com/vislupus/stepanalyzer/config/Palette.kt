package com.vislupus.stepanalyzer.config

import androidx.compose.ui.graphics.Color

/**
 * Colour definitions. An accent is a pair of colours (one for the light theme, a lighter one
 * for the dark theme); the neutral surface colours below are shared by every accent.
 */
data class Accent(val id: String, val light: Color, val dark: Color)

object Palette {
    /** Accent colours the user can pick from. The first one is the default. */
    val accents: List<Accent> = listOf(
        Accent("indigo", Color(0xFF4F5FDB), Color(0xFF9DA7FF)),
        Accent("teal", Color(0xFF0F8A7E), Color(0xFF5CD6C8)),
        Accent("emerald", Color(0xFF1E8E4E), Color(0xFF6ED49A)),
        Accent("amber", Color(0xFFC07A08), Color(0xFFFFC75C)),
        Accent("rose", Color(0xFFD03A6B), Color(0xFFFF8FB3)),
        Accent("slate", Color(0xFF475569), Color(0xFFB4BFD1)),
    )

    fun accent(id: String): Accent = accents.firstOrNull { it.id == id } ?: accents.first()

    // ---- light theme neutrals ----
    object Light {
        val background = Color(0xFFF6F7FA)
        val surface = Color(0xFFFFFFFF)
        val surfaceVariant = Color(0xFFEEF0F5)
        val surfaceContainer = Color(0xFFF1F2F6)
        val onSurface = Color(0xFF17191E)
        val onSurfaceVariant = Color(0xFF5E6472)
        val outline = Color(0xFFD5D9E2)
        val outlineVariant = Color(0xFFE6E9F0)
        val error = Color(0xFFC62828)
        val success = Color(0xFF1E8E4E)
        val warning = Color(0xFFC07A08)
    }

    // ---- dark theme neutrals ----
    object Dark {
        val background = Color(0xFF0F1115)
        val surface = Color(0xFF171A20)
        val surfaceVariant = Color(0xFF22262E)
        val surfaceContainer = Color(0xFF1C2027)
        val onSurface = Color(0xFFE8EAEF)
        val onSurfaceVariant = Color(0xFF9AA2B1)
        val outline = Color(0xFF363B46)
        val outlineVariant = Color(0xFF2A2F38)
        val error = Color(0xFFFF6B6B)
        val success = Color(0xFF6ED49A)
        val warning = Color(0xFFFFC75C)
    }

    /** Series colours used by the charts (index = series). Distinguishable in both themes. */
    val chartSeries: List<Color> = listOf(
        Color(0xFF4F5FDB), Color(0xFFE0566B), Color(0xFF1E9E6A), Color(0xFFE09A1C),
        Color(0xFF8E5BD6), Color(0xFF1AA6C2), Color(0xFFB7572D), Color(0xFF5E6472),
        Color(0xFFD03A9B), Color(0xFF6A8F1E),
    )
}
