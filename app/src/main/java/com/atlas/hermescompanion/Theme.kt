package com.atlas.hermescompanion

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Catppuccin Mocha palette
private val Base      = Color(0xFF1E1E2E)
private val Mantle    = Color(0xFF181825)
private val Crust     = Color(0xFF11111B)
private val Text      = Color(0xFFCDD6F4)
private val Subtext0  = Color(0xFFA6ADC8)
private val Surface0  = Color(0xFF313244)
private val Surface1  = Color(0xFF45475A)
private val Lavender  = Color(0xFFB4BEFE)
private val Mauve     = Color(0xFFCBA6F7)
private val Green     = Color(0xFFA6E3A1)
private val Red       = Color(0xFFF38BA8)
private val Yellow    = Color(0xFFF9E2AF)

private val DarkColorScheme = darkColorScheme(
    primary = Lavender,
    onPrimary = Crust,
    secondary = Mauve,
    onSecondary = Crust,
    background = Base,
    onBackground = Text,
    surface = Mantle,
    onSurface = Text,
    surfaceVariant = Surface0,
    onSurfaceVariant = Subtext0,
    error = Red,
    onError = Crust,
    outline = Surface1,
)

@Composable
fun HermesCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
