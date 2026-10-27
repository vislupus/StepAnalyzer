package com.vislupus.stepanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.config.FontScale
import com.vislupus.stepanalyzer.config.Palette
import com.vislupus.stepanalyzer.config.ThemeMode

/** Extra semantic colours that Material 3 does not define. */
data class ExtraColors(val success: Color, val warning: Color, val chartGrid: Color, val chartRaw: Color)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Palette.Light.success, Palette.Light.warning, Palette.Light.outlineVariant, Palette.Light.onSurfaceVariant)
}

/** Roboto is the Android system sans-serif; `FontFamily.Default` resolves to it. */
private val appFontFamily: FontFamily = FontFamily.Default

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.f(weight: FontWeight? = null) = copy(fontFamily = appFontFamily, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = base.displayLarge.f(FontWeight.SemiBold),
        displayMedium = base.displayMedium.f(FontWeight.SemiBold),
        displaySmall = base.displaySmall.f(FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.f(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.f(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.f(FontWeight.SemiBold),
        titleLarge = base.titleLarge.f(FontWeight.SemiBold),
        titleMedium = base.titleMedium.f(FontWeight.SemiBold),
        titleSmall = base.titleSmall.f(FontWeight.Medium),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(FontWeight.Medium),
        labelMedium = base.labelMedium.f(FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = appFontFamily, letterSpacing = 0.6.sp),
    )
}

private val appShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(AppConfig.Ui.smallCorner),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(AppConfig.Ui.cardCorner),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f).compositeOverWhite(),
    onPrimaryContainer = accent.darken(0.25f),
    secondary = Palette.Light.onSurfaceVariant,
    onSecondary = Color.White,
    secondaryContainer = Palette.Light.surfaceVariant,
    onSecondaryContainer = Palette.Light.onSurface,
    tertiary = accent.darken(0.2f),
    onTertiary = Color.White,
    tertiaryContainer = accent.copy(alpha = 0.08f).compositeOverWhite(),
    onTertiaryContainer = accent.darken(0.3f),
    background = Palette.Light.background,
    onBackground = Palette.Light.onSurface,
    surface = Palette.Light.surface,
    onSurface = Palette.Light.onSurface,
    surfaceVariant = Palette.Light.surfaceVariant,
    onSurfaceVariant = Palette.Light.onSurfaceVariant,
    surfaceContainer = Palette.Light.surfaceContainer,
    surfaceContainerHigh = Palette.Light.surfaceVariant,
    surfaceContainerHighest = Palette.Light.surfaceVariant,
    surfaceContainerLow = Palette.Light.surface,
    surfaceContainerLowest = Palette.Light.surface,
    outline = Palette.Light.outline,
    outlineVariant = Palette.Light.outlineVariant,
    error = Palette.Light.error,
    onError = Color.White,
    errorContainer = Palette.Light.error.copy(alpha = 0.12f).compositeOverWhite(),
    onErrorContainer = Palette.Light.error,
)

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0F1115),
    primaryContainer = accent.copy(alpha = 0.22f).compositeOver(Palette.Dark.surface),
    onPrimaryContainer = accent,
    secondary = Palette.Dark.onSurfaceVariant,
    onSecondary = Palette.Dark.background,
    secondaryContainer = Palette.Dark.surfaceVariant,
    onSecondaryContainer = Palette.Dark.onSurface,
    tertiary = accent,
    onTertiary = Palette.Dark.background,
    tertiaryContainer = accent.copy(alpha = 0.14f).compositeOver(Palette.Dark.surface),
    onTertiaryContainer = accent,
    background = Palette.Dark.background,
    onBackground = Palette.Dark.onSurface,
    surface = Palette.Dark.surface,
    onSurface = Palette.Dark.onSurface,
    surfaceVariant = Palette.Dark.surfaceVariant,
    onSurfaceVariant = Palette.Dark.onSurfaceVariant,
    surfaceContainer = Palette.Dark.surfaceContainer,
    surfaceContainerHigh = Palette.Dark.surfaceVariant,
    surfaceContainerHighest = Palette.Dark.surfaceVariant,
    surfaceContainerLow = Palette.Dark.surface,
    surfaceContainerLowest = Palette.Dark.background,
    outline = Palette.Dark.outline,
    outlineVariant = Palette.Dark.outlineVariant,
    error = Palette.Dark.error,
    onError = Palette.Dark.background,
    errorContainer = Palette.Dark.error.copy(alpha = 0.18f).compositeOver(Palette.Dark.surface),
    onErrorContainer = Palette.Dark.error,
)

private fun Color.compositeOverWhite(): Color = compositeOver(Color.White)

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(
        red = red * a + bg.red * (1 - a),
        green = green * a + bg.green * (1 - a),
        blue = blue * a + bg.blue * (1 - a),
        alpha = 1f,
    )
}

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)

/** Whether the app is currently rendering in dark mode for the given setting. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun StepAnalyzerTheme(
    themeMode: ThemeMode,
    accentId: String,
    fontScale: FontScale,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val accent = Palette.accent(accentId)
    val scheme = if (dark) darkScheme(accent.dark) else lightScheme(accent.light)
    val extra = if (dark) {
        ExtraColors(Palette.Dark.success, Palette.Dark.warning, Palette.Dark.outlineVariant, Palette.Dark.onSurfaceVariant)
    } else {
        ExtraColors(Palette.Light.success, Palette.Light.warning, Palette.Light.outlineVariant, Palette.Light.onSurfaceVariant)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalExtraColors provides extra,
        LocalDensity provides Density(density.density, density.fontScale * fontScale.factor),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography(), shapes = appShapes, content = content)
    }
}

/** Convenience accessor for the extra colours. */
val MaterialTheme.extra: ExtraColors
    @Composable get() = LocalExtraColors.current
