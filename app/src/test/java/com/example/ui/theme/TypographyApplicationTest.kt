package com.example.ui.theme

import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypographyApplicationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `plain text inherits Lato from app theme`() {
        var renderedFontFamily: FontFamily? = null

        composeRule.setContent {
            MyApplicationTheme {
                Text("Body copy", onTextLayout = { renderedFontFamily = it.layoutInput.style.fontFamily })
            }
        }

        composeRule.waitForIdle()
        assertEquals(Lato, renderedFontFamily)
    }

    @Test
    fun `AppHeading renders with Lexend while preserving custom size`() {
        var renderedFontFamily: FontFamily? = null
        var renderedFontSize = 0f

        composeRule.setContent {
            MyApplicationTheme {
                AppHeading(
                    text = "Screen title",
                    style = Typography.titleLarge.copy(fontSize = 24.sp),
                    onTextLayout = {
                        renderedFontFamily = it.layoutInput.style.fontFamily
                        renderedFontSize = it.layoutInput.style.fontSize.value
                    }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(Lexend, renderedFontFamily)
        assertEquals(24f, renderedFontSize)
    }

    @Test
    fun `Material button labels use Poppins`() {
        var renderedFontFamily: FontFamily? = null

        composeRule.setContent {
            MyApplicationTheme {
                Button(onClick = {}) {
                    Text("Continue", onTextLayout = { renderedFontFamily = it.layoutInput.style.fontFamily })
                }
            }
        }

        composeRule.waitForIdle()
        assertEquals(Poppins, renderedFontFamily)
    }

    @Test
    fun `AppLabel renders with Poppins by default`() {
        var renderedFontFamily: FontFamily? = null

        composeRule.setContent {
            MyApplicationTheme {
                AppLabel("Settings", onTextLayout = { renderedFontFamily = it.layoutInput.style.fontFamily })
            }
        }

        composeRule.waitForIdle()
        assertEquals(Poppins, renderedFontFamily)
    }
}
