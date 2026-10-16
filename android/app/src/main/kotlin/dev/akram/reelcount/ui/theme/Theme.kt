package dev.akram.reelcount.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1F7A4D)
private val GreenDark = Color(0xFF8FD9AE)
private val Amber = Color(0xFFB86E00)
private val Ink = Color(0xFF14201A)
private val Paper = Color(0xFFF3F6F3)
private val Night = Color(0xFF101613)
private val NightCard = Color(0xFF1B2420)

private val LightColors =
    lightColorScheme(
        primary = Green,
        onPrimary = Color.White,
        secondary = Amber,
        background = Paper,
        surface = Color.White,
        onBackground = Ink,
        onSurface = Ink,
    )

private val DarkColors =
    darkColorScheme(
        primary = GreenDark,
        onPrimary = Color(0xFF08331C),
        secondary = Color(0xFFE6B15A),
        background = Night,
        surface = NightCard,
        onBackground = Color(0xFFE7F0EA),
        onSurface = Color(0xFFE7F0EA),
    )

@Composable
fun ReelCountTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
