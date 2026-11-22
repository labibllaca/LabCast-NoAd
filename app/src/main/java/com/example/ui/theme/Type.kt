package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// App typefaces. Only these three families may be used anywhere in the app.
val Lexend =
  FontFamily(
    Font(R.font.lexend_400, FontWeight.Normal),
    Font(R.font.lexend_500, FontWeight.Medium),
    Font(R.font.lexend_600, FontWeight.SemiBold),
    Font(R.font.lexend_700, FontWeight.Bold),
    Font(R.font.lexend_900, FontWeight.Black),
  )

val Poppins =
  FontFamily(
    Font(R.font.poppins_400, FontWeight.Normal),
    Font(R.font.poppins_500, FontWeight.Medium),
    Font(R.font.poppins_600, FontWeight.SemiBold),
    Font(R.font.poppins_700, FontWeight.Bold),
    Font(R.font.poppins_900, FontWeight.Black),
  )

val Lato =
  FontFamily(
    Font(R.font.lato_400, FontWeight.Normal),
    Font(R.font.lato_500, FontWeight.Medium),
    Font(R.font.lato_600, FontWeight.SemiBold),
    Font(R.font.lato_700, FontWeight.Bold),
    Font(R.font.lato_900, FontWeight.Black),
  )

// Defaults keep the Material 3 metrics; only the typefaces change.
private val Defaults = Typography()

// Lexend for display/headlines/titles, Poppins for labels/buttons, Lato for body text.
val Typography =
  Typography(
    displayLarge = Defaults.displayLarge.copy(fontFamily = Lexend),
    displayMedium = Defaults.displayMedium.copy(fontFamily = Lexend),
    displaySmall = Defaults.displaySmall.copy(fontFamily = Lexend),
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = Lexend),
    headlineMedium = Defaults.headlineMedium.copy(fontFamily = Lexend),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = Lexend),
    titleLarge = Defaults.titleLarge.copy(fontFamily = Lexend),
    titleMedium = Defaults.titleMedium.copy(fontFamily = Lexend),
    titleSmall = Defaults.titleSmall.copy(fontFamily = Lexend),
    bodyLarge = Defaults.bodyLarge.copy(fontFamily = Lato, letterSpacing = 0.5.sp),
    bodyMedium = Defaults.bodyMedium.copy(fontFamily = Lato),
    bodySmall = Defaults.bodySmall.copy(fontFamily = Lato),
    labelLarge = Defaults.labelLarge.copy(fontFamily = Poppins),
    labelMedium = Defaults.labelMedium.copy(fontFamily = Poppins),
    labelSmall = Defaults.labelSmall.copy(fontFamily = Poppins),
  )
