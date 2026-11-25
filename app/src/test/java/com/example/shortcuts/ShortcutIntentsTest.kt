package com.example.shortcuts

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutIntentsTest {
    @Test
    fun consumesTheEpisodeIdOnce() {
        val intent = Intent().putExtra(AppShortcuts.EXTRA_EPISODE_ID, "ep-1")

        assertEquals("ep-1", ShortcutIntents.consume(intent))
        assertNull(ShortcutIntents.consume(intent))
    }

    @Test
    fun ignoresIntentsWithoutTheEpisodeId() {
        assertNull(ShortcutIntents.consume(Intent()))
        assertNull(ShortcutIntents.consume(null))
    }
}
