package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.data.ManualAdRange
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualMarksBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val first = ManualAdRange(1_396_000L, 1_556_000L)   // 23:16 – 25:56
    private val second = ManualAdRange(2_680_000L, 2_847_000L)  // 44:40 – 47:27

    @Test
    fun sheetListsEveryRangeAndForwardsNudgeAndDelete() {
        var nudgeCall: Triple<ManualAdRange, Boolean, Long>? = null
        var deleted: ManualAdRange? = null

        composeRule.setContent {
            MyApplicationTheme {
                ManualMarksBottomSheet(
                    ranges = listOf(first, second),
                    durationMs = 3_600_000L,
                    formatTime = ::formatTime,
                    onNudge = { range, isStart, delta -> nudgeCall = Triple(range, isStart, delta) },
                    onDelete = { deleted = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("manual_marks_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("23:16 – 25:56").assertIsDisplayed()
        composeRule.onNodeWithText("44:40 – 47:27").assertIsDisplayed()
        composeRule.onAllNodesWithTag("manual_marks_row").assertCountEquals(2)

        composeRule.onAllNodesWithTag("manual_ad_start_plus")[0].performClick()
        assertEquals(Triple(first, true, 1_000L), nudgeCall)
        composeRule.onAllNodesWithTag("manual_ad_end_plus")[1].performClick()
        assertEquals(Triple(second, false, 1_000L), nudgeCall)
        composeRule.onAllNodesWithTag("manual_ad_delete")[0].performClick()
        assertEquals(first, deleted)
    }

    @Test
    fun emptySheetShowsEmptyState() {
        composeRule.setContent {
            MyApplicationTheme {
                ManualMarksBottomSheet(
                    ranges = emptyList(),
                    durationMs = 3_600_000L,
                    formatTime = ::formatTime,
                    onNudge = { _: ManualAdRange, _: Boolean, _: Long -> },
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("manual_marks_empty").assertIsDisplayed()
    }

    @Test
    fun landscapeSheetStaysVisibleAndDismissible() {
        var dismissed = false
        var maxHeightDp = 0f
        composeRule.setContent {
            maxHeightDp = LocalConfiguration.current.screenHeightDp * 0.8f
            CompositionLocalProvider(LocalIsLandscape provides true) {
                MyApplicationTheme {
                    ManualMarksBottomSheet(
                        ranges = List(12) { index ->
                            ManualAdRange(index * 10_000L, index * 10_000L + 5_000L)
                        },
                        durationMs = 3_600_000L,
                        formatTime = ::formatTime,
                        onNudge = { _: ManualAdRange, _: Boolean, _: Long -> },
                        onDelete = {},
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }

        val sheetHeight = composeRule.onNodeWithTag("manual_marks_sheet")
            .fetchSemanticsNode().boundsInRoot.height
        val maxHeightPx = with(composeRule.density) { maxHeightDp.dp.toPx() }
        assertTrue(
            "Landscape sheet height $sheetHeight should be at most $maxHeightPx",
            sheetHeight <= maxHeightPx
        )
        composeRule.onNodeWithTag("manual_marks_dismiss").performClick()
        assertTrue(dismissed)
    }

    private fun formatTime(ms: Long): String =
        "${ms / 60_000L}:${"%02d".format(ms % 60_000L / 1_000L)}"
}
