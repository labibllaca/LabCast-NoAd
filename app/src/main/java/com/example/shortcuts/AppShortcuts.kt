package com.example.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R

object AppShortcuts {
    const val EXTRA_EPISODE_ID = "com.example.extra.SHORTCUT_EPISODE_ID"

    fun publish(context: Context, specs: List<ShortcutSpec>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val limit = minOf(
            RecentEpisodes.MAX_SHORTCUTS,
            ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        )
        val shortcuts = specs.take(limit).mapIndexed { index, spec ->
            ShortcutInfoCompat.Builder(context, spec.id)
                .setShortLabel(spec.shortLabel)
                .setLongLabel(spec.longLabel)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_play_arrow))
                .setRank(index)
                .setIntent(intentFor(context, spec.episodeId))
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun intentFor(context: Context, episodeId: String) =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("labcast://shortcut/episode/${Uri.encode(episodeId)}")
            putExtra(EXTRA_EPISODE_ID, episodeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
