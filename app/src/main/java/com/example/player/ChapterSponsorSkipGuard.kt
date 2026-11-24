package com.example.player

import com.example.data.PodcastChapter

/** Prevents repeated seeks within one episode without suppressing matching chapter names elsewhere. */
internal class ChapterSponsorSkipGuard {
    private var lastSkippedKey: String? = null

    fun claim(episodeId: String, chapter: PodcastChapter): Boolean {
        val chapterIdentity = chapter.id.ifBlank { chapter.title }
        val key = "$episodeId:$chapterIdentity"
        if (lastSkippedKey == key) return false

        lastSkippedKey = key
        return true
    }

    fun reset() {
        lastSkippedKey = null
    }
}
