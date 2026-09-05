package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val title: String) {
    DARK("Dark Cyber"),
    LIGHT("Light Clean"),
    SYSTEM("System Default")
}

private val DarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    secondary = CyberGreenGlow,
    tertiary = AdGold,
    background = ObsidianBlack,
    surface = DarkCharcoal,
    surfaceVariant = Color(0xFF1E212B),
    outline = BorderGray,
    onPrimary = ObsidianBlack,
    onSecondary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextGray,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = CyberGreen,
    tertiary = AdGold,
    background = LightCanvas,
    surface = LightSurface,
    surfaceVariant = LightCard,
    outline = LightBorder,
    onPrimary = Color.White,
    onSecondary = LightTextPrimary,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary,
    error = ErrorRed
)

data class CustomThemeColors(
    val isDark: Boolean,
    val cardBackground: Color,
    val itemBorder: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val miniPlayerBg: Color,
    val topBarBg: Color,
    val inputBg: Color
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomThemeColors(
        isDark = true,
        cardBackground = DarkCharcoal,
        itemBorder = BorderGray,
        textPrimary = TextWhite,
        textMuted = TextGray,
        miniPlayerBg = DarkCharcoal,
        topBarBg = ObsidianBlack,
        inputBg = DarkCharcoal
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val customColors = if (isDark) {
        CustomThemeColors(
            isDark = true,
            cardBackground = DarkCharcoal,
            itemBorder = BorderGray,
            textPrimary = TextWhite,
            textMuted = TextGray,
            miniPlayerBg = DarkCharcoal,
            topBarBg = ObsidianBlack,
            inputBg = DarkCharcoal
        )
    } else {
        CustomThemeColors(
            isDark = false,
            cardBackground = LightSurface,
            itemBorder = LightBorder,
            textPrimary = LightTextPrimary,
            textMuted = LightTextSecondary,
            miniPlayerBg = LightSurface,
            topBarBg = LightCanvas,
            inputBg = LightCard
        )
    }

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
