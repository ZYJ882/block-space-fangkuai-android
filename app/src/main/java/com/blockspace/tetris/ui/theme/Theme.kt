package com.blockspace.tetris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TetrisColors = darkColorScheme(
    primary = Color(0xFF32D7FF),
    onPrimary = Color(0xFF001E27),
    secondary = Color(0xFFC77DFF),
    onSecondary = Color(0xFF2A003F),
    tertiary = Color(0xFFFFD84D),
    background = Color(0xFF08111D),
    onBackground = Color(0xFFEAF4FF),
    surface = Color(0xFF101C2B),
    onSurface = Color(0xFFEAF4FF),
    surfaceVariant = Color(0xFF1B2B3F),
    onSurfaceVariant = Color(0xFFBDCBDE),
    outline = Color(0xFF36506A),
    error = Color(0xFFFF6B7C)
)

@Composable
fun TetrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TetrisColors,
        typography = TetrisTypography,
        content = content
    )
}
