package com.airtouch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF14232B)
private val Teal = Color(0xFF0F6E56)
private val TealBright = Color(0xFF7DDEBF)
private val Sand = Color(0xFFF4F1EA)
private val Paper = Color(0xFFFFFCF7)
private val Clay = Color(0xFFB5523A)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Clay,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = TealBright,
    onPrimary = Color(0xFF00382C),
    secondary = Color(0xFFFFB4A2),
    onSecondary = Color(0xFF5C1A10),
    background = Color(0xFF10181C),
    onBackground = Color(0xFFE6EEF0),
    surface = Color(0xFF1A2429),
    onSurface = Color(0xFFE6EEF0),
)

@Composable
fun AirTouchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
