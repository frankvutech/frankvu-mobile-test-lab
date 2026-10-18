package com.mobiletestplayground.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A fixed palette rather than dynamic colour, so screenshots are identical across devices.
private val Indigo = Color(0xFF3F51B5)
private val IndigoLight = Color(0xFFB8C4FF)
private val Teal = Color(0xFF00796B)
private val TealLight = Color(0xFF80CBC4)
private val ErrorRed = Color(0xFFB3261E)
private val ErrorRedLight = Color(0xFFF2B8B5)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Teal,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    secondary = TealLight,
    error = ErrorRedLight,
)

@Composable
fun PlaygroundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
