package com.example.shortcuts

import android.app.Application
import android.content.pm.ShortcutManager
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShortcutsTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun publishesThreeOrderedShortcutsWithLabelsAndUniqueIntents() {
        AppShortcuts.publish(context, fiveSpecs())

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        val byRank = shortcuts.sortedBy { it.rank }

        assertEquals(setOf("episode_a", "episode_b", "episode_c"), shortcuts.map { it.id }.toSet())
        assertEquals(listOf("episode_a", "episode_b", "episode_c"), byRank.map { it.id })
        assertEquals(listOf(0, 1, 2), byRank.map { it.rank })
        assertEquals("Short a", byRank.first().shortLabel.toString())
        assertEquals(
            listOf("a", "b", "c"),
            byRank.map { it.intent.getStringExtra(AppShortcuts.EXTRA_EPISODE_ID) }
        )
        assertEquals(3, shortcuts.mapNotNull { it.intent.data }.distinct().size)
    }

    @Test
    fun publishReplacesThePreviousSet() {
        AppShortcuts.publish(context, fiveSpecs())
        assertEquals(3, ShortcutManagerCompat.getDynamicShortcuts(context).size)

        AppShortcuts.publish(context, listOf(fiveSpecs().first()))

        val remaining = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(listOf("episode_a"), remaining.map { it.id })
    }

    @Test
    fun clampsToTheLauncherLimit() {
        val manager = context.getSystemService(ShortcutManager::class.java)
        Shadows.shadowOf(manager).setMaxShortcutCountPerActivity(2)

        AppShortcuts.publish(context, fiveSpecs())

        assertEquals(2, ShortcutManagerCompat.getDynamicShortcuts(context).size)
    }

    @Test
    @Config(sdk = [24])
    fun doesNothingOnApi24() {
        AppShortcuts.publish(context, fiveSpecs())

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    private fun fiveSpecs() = ('a'..'e').map { c ->
        ShortcutSpec(
            id = "episode_$c",
            shortLabel = "Short $c",
            longLabel = "Long $c",
            episodeId = c.toString()
        )
    }
}
