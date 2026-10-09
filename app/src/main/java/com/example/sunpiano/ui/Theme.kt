package com.example.sunpiano.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB74D)
val Ink = Color(0xFF101418)
val PanelColor = Color(0xFF1B2128)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1800),
    secondary = Color(0xFF80CBC4),
    background = Ink,
    surface = PanelColor,
    surfaceVariant = Color(0xFF28313A),
    onSurface = Color(0xFFE6E9EC),
    error = Color(0xFFFF6B6B),
)

@Composable
fun PanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
