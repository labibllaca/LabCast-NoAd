package com.example.asr

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AsrPreferencesTest {

    @Test
    fun `enable flag persists`() {
        val prefs = AsrPreferences(ApplicationProvider.getApplicationContext())
        prefs.enabled = true
        assertTrue(AsrPreferences(ApplicationProvider.getApplicationContext()).enabled)
    }
}
