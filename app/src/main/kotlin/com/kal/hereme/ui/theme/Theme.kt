package com.kal.hereme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Mint = Color(0xFF3DDC97)
private val MintDark = Color(0xFF12855A)
private val Warning = Color(0xFFFFB300)
private val Danger = Color(0xFFFF5C5C)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF04140D),
    secondary = Warning,
    error = Danger,
    background = Color(0xFF0D1114),
    surface = Color(0xFF141A1E),
    onBackground = Color(0xFFF4F7F9),
    onSurface = Color(0xFFF4F7F9),
    onSurfaceVariant = Color(0xFF9AA6AE),
)

private val LightColors = lightColorScheme(
    primary = MintDark,
    onPrimary = Color.White,
    secondary = Color(0xFF9A6B00),
    error = Color(0xFFC0392B),
    background = Color(0xFFF7F9FA),
    surface = Color.White,
    onBackground = Color(0xFF10171C),
    onSurface = Color(0xFF10171C),
    onSurfaceVariant = Color(0xFF5A666E),
)

@Composable
fun HereMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
