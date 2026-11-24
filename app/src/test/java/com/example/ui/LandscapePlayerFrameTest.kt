package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LandscapePlayerFrameTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun artworkSlotSitsLeftOfBodySlot() {
        composeRule.setContent {
            MyApplicationTheme {
                LandscapePlayerFrame(
                    artwork = { Box(Modifier.size(60.dp)) },
                    body = { Box(Modifier.size(60.dp)) }
                )
            }
        }

        composeRule.onNodeWithTag("landscape_player_frame").assertIsDisplayed()
        val artwork = composeRule.onNodeWithTag("landscape_player_artwork").fetchSemanticsNode().boundsInRoot
        val body = composeRule.onNodeWithTag("landscape_player_body").fetchSemanticsNode().boundsInRoot
        assertTrue("Artwork must be left of body: $artwork vs $body", artwork.right <= body.left)
    }
}
