package com.rishinnovations.isai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.rishinnovations.isai.model.ThemeMode

/** ISAI design tokens. Every colour used in the UI comes from here or from the Material scheme below. */
object IsaiPalette {
    val Black = Color(0xFF090908)
    val Charcoal = Color(0xFF151513)
    val CharcoalHigh = Color(0xFF1F1E1A)
    val Ivory = Color(0xFFF4EEDD)
    val IvoryMuted = Color(0xFFB8B2A2)
    val Gold = Color(0xFFC6A15B)
    val GoldDeep = Color(0xFF7D6120)
    val Lavender = Color(0xFFB8A9E8)
    val LavenderDeep = Color(0xFF5B4B9A)
    val LightBackground = Color(0xFFFAF6EC)
    val LightSurface = Color(0xFFF1EBDB)
    val LightSurfaceHigh = Color(0xFFE8E0CC)
    val Ink = Color(0xFF1B1A16)
    val InkMuted = Color(0xFF5E5A4D)
}

@Immutable
class IsaiColors(
    val gold: Color,
    val goldSoft: Color,
    val muted: Color,
    val lavender: Color,
    val glass: Color,
    val glassBorder: Color
)

private val DarkIsaiColors = IsaiColors(
    gold = IsaiPalette.Gold,
    goldSoft = IsaiPalette.Gold.copy(alpha = 0.16f),
    muted = IsaiPalette.IvoryMuted,
    lavender = IsaiPalette.Lavender,
    glass = Color.White.copy(alpha = 0.10f),
    glassBorder = Color.White.copy(alpha = 0.18f)
)

private val LightIsaiColors = IsaiColors(
    gold = IsaiPalette.GoldDeep,
    goldSoft = IsaiPalette.GoldDeep.copy(alpha = 0.12f),
    muted = IsaiPalette.InkMuted,
    lavender = IsaiPalette.LavenderDeep,
    glass = Color.Black.copy(alpha = 0.06f),
    glassBorder = Color.Black.copy(alpha = 0.12f)
)

val LocalIsaiColors = staticCompositionLocalOf { DarkIsaiColors }
val LocalReduceMotion = staticCompositionLocalOf { false }

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = IsaiPalette.Gold,
    onPrimary = IsaiPalette.Black,
    primaryContainer = IsaiPalette.Gold.copy(alpha = 0.18f),
    onPrimaryContainer = IsaiPalette.Gold,
    secondary = IsaiPalette.Lavender,
    onSecondary = IsaiPalette.Black,
    background = IsaiPalette.Black,
    onBackground = IsaiPalette.Ivory,
    surface = IsaiPalette.Black,
    onSurface = IsaiPalette.Ivory,
    surfaceVariant = IsaiPalette.Charcoal,
    onSurfaceVariant = IsaiPalette.IvoryMuted,
    surfaceContainer = IsaiPalette.Charcoal,
    surfaceContainerHigh = IsaiPalette.CharcoalHigh,
    surfaceContainerHighest = IsaiPalette.CharcoalHigh,
    surfaceContainerLow = IsaiPalette.Charcoal,
    outline = IsaiPalette.IvoryMuted.copy(alpha = 0.5f),
    outlineVariant = IsaiPalette.IvoryMuted.copy(alpha = 0.2f),
    error = Color(0xFFFF8A80)
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = IsaiPalette.GoldDeep,
    onPrimary = Color.White,
    primaryContainer = IsaiPalette.GoldDeep.copy(alpha = 0.14f),
    onPrimaryContainer = IsaiPalette.GoldDeep,
    secondary = IsaiPalette.LavenderDeep,
    onSecondary = Color.White,
    background = IsaiPalette.LightBackground,
    onBackground = IsaiPalette.Ink,
    surface = IsaiPalette.LightBackground,
    onSurface = IsaiPalette.Ink,
    surfaceVariant = IsaiPalette.LightSurface,
    onSurfaceVariant = IsaiPalette.InkMuted,
    surfaceContainer = IsaiPalette.LightSurface,
    surfaceContainerHigh = IsaiPalette.LightSurfaceHigh,
    surfaceContainerHighest = IsaiPalette.LightSurfaceHigh,
    surfaceContainerLow = IsaiPalette.LightSurface,
    outline = IsaiPalette.InkMuted.copy(alpha = 0.5f),
    outlineVariant = IsaiPalette.InkMuted.copy(alpha = 0.2f),
    error = Color(0xFFB3261E)
)

/** One type scale for the whole app: page titles, section headings, song titles, metadata, buttons. */
val IsaiTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

@Composable
fun IsaiTheme(mode: ThemeMode, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalIsaiColors provides if (dark) DarkIsaiColors else LightIsaiColors,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = IsaiTypography, content = content)
    }
}

val MaterialTheme.isai: IsaiColors
    @Composable get() = LocalIsaiColors.current
