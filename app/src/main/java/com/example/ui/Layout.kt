package com.example.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True when the activity is laid out in landscape; portrait is the default everywhere. */
val LocalIsLandscape = staticCompositionLocalOf { false }

object LandscapeLayout {
    fun isLandscape(configuration: Configuration): Boolean =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun screenPadding(isLandscape: Boolean): PaddingValues =
        if (isLandscape) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(16.dp)

    fun listMaxWidth(isLandscape: Boolean): Dp = if (isLandscape) 600.dp else Dp.Unspecified

    fun sheetMaxWidth(isLandscape: Boolean): Dp = if (isLandscape) 600.dp else Dp.Unspecified

    /** Portrait keeps its original horizontal padding; landscape gets compact padding and a capped width. */
    fun listScreenModifier(isLandscape: Boolean, portraitHorizontal: Dp = 16.dp): Modifier =
        if (isLandscape) {
            Modifier
                .fillMaxSize()
                .padding(screenPadding(true))
                .widthIn(max = listMaxWidth(true))
        } else {
            Modifier.fillMaxSize().padding(horizontal = portraitHorizontal)
        }

    fun playerArtworkSize(isLandscape: Boolean, screenHeightDp: Dp): Dp =
        if (!isLandscape) 280.dp
        else (screenHeightDp * 0.6f).coerceAtLeast(96.dp).coerceAtMost(200.dp)
}
