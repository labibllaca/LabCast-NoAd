package com.example.shortcuts

import com.example.data.EpisodeEntity

data class ShortcutSpec(
    val id: String,
    val shortLabel: String,
    val longLabel: String,
    val episodeId: String
)

object RecentEpisodes {
    const val MAX_SHORTCUTS = 3

    fun select(episodes: List<EpisodeEntity>, limit: Int = MAX_SHORTCUTS): List<EpisodeEntity> =
        episodes.filter { it.lastPlayedAt > 0L }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<EpisodeEntity> { it.lastPlayedAt }.thenBy { it.id })
            .take(limit.coerceAtLeast(0))

    fun shortcutSpecs(episodes: List<EpisodeEntity>, limit: Int = MAX_SHORTCUTS): List<ShortcutSpec> =
        select(episodes, limit).map { ep ->
            ShortcutSpec(
                id = "episode_${ep.id}",
                shortLabel = label(ep.title, 40),
                longLabel = label("${ep.podcastTitle} · ${ep.title}", 80),
                episodeId = ep.id
            )
        }

    private fun label(text: String, max: Int): String {
        val clean = text.trim().replace(Regex("\\s+"), " ").ifBlank { "Episode" }
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }
}
