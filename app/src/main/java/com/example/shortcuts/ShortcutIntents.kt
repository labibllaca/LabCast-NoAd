package com.example.shortcuts

import android.content.Intent

/**
 * Reads a shortcut's episode id and removes it from the intent so an activity recreation (rotation,
 * process-death restore) cannot replay the shortcut and restart playback.
 */
object ShortcutIntents {
    fun consume(intent: Intent?): String? {
        val id = intent?.getStringExtra(AppShortcuts.EXTRA_EPISODE_ID) ?: return null
        intent.removeExtra(AppShortcuts.EXTRA_EPISODE_ID)
        return id
    }
}
