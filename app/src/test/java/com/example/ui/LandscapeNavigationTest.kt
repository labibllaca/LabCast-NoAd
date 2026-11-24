package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
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
}
