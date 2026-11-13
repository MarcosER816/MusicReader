package com.musicreader.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFDD835),
    onPrimary = Color(0xFF1B1B1B),
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF0B1F2A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
)

/** MusicReader theme: dark by default (Stage 0 decision, see docs/DECISIONS.md). */
@Composable
fun MusicReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
