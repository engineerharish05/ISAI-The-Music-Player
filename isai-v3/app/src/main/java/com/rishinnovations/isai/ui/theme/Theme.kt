package com.rishinnovations.isai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFD6A84F)

private val Dark = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF181208),
    background = Color(0xFF080808),
    surface = Color(0xFF121212),
    onBackground = Color(0xFFF7F7F7),
    onSurface = Color(0xFFF7F7F7),
    onSurfaceVariant = Color(0xFFB8B8B8)
)

private val Light = lightColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF181208),
    background = Color(0xFFF7F6F2),
    surface = Color.White,
    onBackground = Color(0xFF151515),
    onSurface = Color(0xFF151515),
    onSurfaceVariant = Color(0xFF666666)
)

@Composable
fun ISAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
