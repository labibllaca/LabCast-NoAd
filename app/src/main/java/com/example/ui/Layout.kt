package com.example.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True when the activity is laid out in landscape; portrait is the default everywhere. */
val LocalIsLandscape = staticCompositionLocalOf { false }

/** Side-by-side player layout used only in landscape; portrait keeps its single column. */
@Composable
fun LandscapePlayerFrame(
    artwork: @Composable () -> Unit,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxHeight().testTag("landscape_player_frame")
    ) {
        Box(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .testTag("landscape_player_artwork")
        ) { artwork() }
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .testTag("landscape_player_body")
        ) { body() }
    }
}

object LandscapeLayout {
    fun isLandscape(configuration: Configuration): Boolean =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun screenPadding(isLandscape: Boolean): PaddingValues =
        if (isLandscape) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(16.dp)

    fun listMaxWidth(isLandscape: Boolean): Dp = if (isLandscape) 600.dp else Dp.Unspecified

    fun sheetMaxWidth(isLandscape: Boolean): Dp = if (isLandscape) 600.dp else Dp.Unspecified

    fun sheetModifier(isLandscape: Boolean, tag: String): Modifier =
        Modifier
            .testTag(tag)
            .then(if (isLandscape) Modifier.widthIn(max = sheetMaxWidth(true)) else Modifier)

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
