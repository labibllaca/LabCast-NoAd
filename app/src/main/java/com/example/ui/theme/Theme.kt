package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    secondary = CyberGreenGlow,
    tertiary = AdGold,
    background = ObsidianBlack,
    surface = DarkCharcoal,
    onPrimary = ObsidianBlack,
    onSecondary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = ErrorRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Theme for the DarkCast aesthetic!
    dynamicColor: Boolean = false, // Use our curated cyber-midnight palette instead of standard dynamic overlays
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
