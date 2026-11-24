package com.example.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.TranscriptParser
import com.example.data.TranscriptSegment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptCardLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transcriptCardWrapsItsContent() {
        composeRule.setContent {
            MyApplicationTheme {
                ModalBottomSheet(
                    onDismissRequest = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .testTag("segment_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Surface { Text("00:00") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pre-Roll Sponsor (BetterHelp)")
                                }
                                Button(onClick = {}, modifier = Modifier.height(26.dp)) {
                                    Text("Mark Ad")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("This episode is sponsored by BetterHelp. Support for today's show comes from BetterHelp.")
                        }
                    }
                }
                }
                }
            }
        }

        val cardHeight = composeRule.onNodeWithTag("segment_card")
            .fetchSemanticsNode().boundsInRoot.height
        println("DBG card height=$cardHeight")
        assertTrue("Card should wrap content, got $cardHeight px", cardHeight < 200f)
    }

    @Test
    fun realTranscriptSheetCardWrapsItsContent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = PodcastViewModel(app)
        val raw = "00:00 [Pre-Roll Sponsor (BetterHelp) [Sponsor Break [Sponsor Break]] ] " +
            "This episode is sponsored by BetterHelp. Support for today's show comes from BetterHelp. " +
            "Use promo code PODCAST for an exclusive discount.\n" +
            "01:15 [Host] Welcome back to The Jordan Harbinger Show! In today's episode '1418: Steven Rosenbaum | How AI Reshapes Reality (and Our Trust in It)', we dive deep into the key ideas and key takeaways.\n" +
            "18:10 [Host [Sponsor Break] [LabCast:NotAd] ] Before we continue, a quick word from our sponsor. Thanks to AG1 and BetterHelp for sponsoring this segment.\n" +
            "19:10 [Host] Now back to our discussion. Let's analyze the second main core topic of today's show.\n" +
            "49:59 [Host [Sponsor Break]] Sponsored by BetterHelp. Visit betterhelp.com."
        val segments = TranscriptParser.parseTranscript(raw)

        composeRule.setContent {
            MyApplicationTheme {
                TranscriptBottomSheet(
                    transcriptSegments = segments,
                    playbackPositionMs = 0L,
                    episodeTitle = "Test episode",
                    viewModel = viewModel,
                    onSegmentSelected = {},
                    onDismiss = {}
                )
            }
        }

        val cardHeight = composeRule.onNodeWithTag("transcript_item_0")
            .fetchSemanticsNode().boundsInRoot.height
        println("DBG real card height=$cardHeight")
        assertTrue("Real transcript card should wrap content, got $cardHeight px", cardHeight < 300f)
    }
}
