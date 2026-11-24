package com.example.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeLayoutTest {

    @Test
    fun detectsLandscapeAndPortraitConfiguration() {
        val landscape = Configuration().apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        val portrait = Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT }

        assertEquals(true, LandscapeLayout.isLandscape(landscape))
        assertEquals(false, LandscapeLayout.isLandscape(portrait))
    }

    @Test
    fun screenPaddingIsCompactOnlyInLandscape() {
        assertEquals(PaddingValues(horizontal = 12.dp, vertical = 8.dp), LandscapeLayout.screenPadding(true))
        assertEquals(PaddingValues(16.dp), LandscapeLayout.screenPadding(false))
    }

    @Test
    fun widthsAreCappedOnlyInLandscape() {
        assertEquals(600.dp, LandscapeLayout.listMaxWidth(true))
        assertEquals(600.dp, LandscapeLayout.sheetMaxWidth(true))
        assertEquals(Dp.Unspecified, LandscapeLayout.listMaxWidth(false))
        assertEquals(Dp.Unspecified, LandscapeLayout.sheetMaxWidth(false))
    }

    @Test
    fun artworkSizeFollowsLandscapeHeightAndClamps() {
        assertEquals(180.dp, LandscapeLayout.playerArtworkSize(true, 300.dp))
        assertEquals(200.dp, LandscapeLayout.playerArtworkSize(true, 600.dp))
        assertEquals(200.dp, LandscapeLayout.playerArtworkSize(true, 1000.dp))
        assertEquals(96.dp, LandscapeLayout.playerArtworkSize(true, 100.dp))
        assertEquals(280.dp, LandscapeLayout.playerArtworkSize(false, 300.dp))
    }
}
