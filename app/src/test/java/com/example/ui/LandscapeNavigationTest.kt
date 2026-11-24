package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
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
class LandscapeNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun landscapeNavBarKeepsTagsAndFitsSmallHeight() {
        composeRule.setContent {
            CompositionLocalProvider(LocalIsLandscape provides true) {
                MyApplicationTheme {
                    FloatingPillNavigationBar(
                        activeTab = PodcastViewModel.Tab.DISCOVER,
                        isAlbanian = false,
                        onTabSelected = {},
                        onSearchClick = {}
                    )
                }
            }
        }

        listOf(
            "floating_nav_dock",
            "floating_search_pill",
            "nav_tab_discover",
            "nav_tab_offline",
            "nav_tab_verlauf",
            "nav_tab_settings",
            "btn_floating_search"
        ).forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }

        val heightPx = composeRule.onNodeWithTag("floating_nav_dock").fetchSemanticsNode().size.height
        val maxHeightPx = with(composeRule.density) { 56.dp.toPx() }
        assertTrue("Landscape nav dock too tall: $heightPx px", heightPx <= maxHeightPx)
    }

    @Test
    fun landscapeNavItemsFitDockAtNormalFontScale() = assertItemsFitDock(1.0f)

    @Test
    fun landscapeNavItemsFitDockAtLargeFontScale() = assertItemsFitDock(1.3f)

    private fun assertItemsFitDock(fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalIsLandscape provides true,
                LocalDensity provides Density(density = 1f, fontScale = fontScale)
            ) {
                MyApplicationTheme {
                    FloatingPillNavigationBar(
                        activeTab = PodcastViewModel.Tab.DISCOVER,
                        isAlbanian = false,
                        onTabSelected = {},
                        onSearchClick = {}
                    )
                }
            }
        }

        val dock = composeRule.onNodeWithTag("floating_nav_dock")
            .fetchSemanticsNode().boundsInRoot
        val label = AppLanguage.getTabLabel(false, PodcastViewModel.Tab.DISCOVER)
        val icon = composeRule.onNodeWithContentDescription(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val text = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Font scale $fontScale: icon $icon exceeds dock $dock",
            icon.top >= dock.top && icon.bottom <= dock.bottom
        )
        val contentBottomLimit = dock.top + with(composeRule.density) { 40.dp.toPx() }
        assertTrue(
            "Font scale $fontScale: label $text exceeds compact dock budget $contentBottomLimit",
            text.top >= dock.top && text.bottom <= contentBottomLimit
        )
    }
}
