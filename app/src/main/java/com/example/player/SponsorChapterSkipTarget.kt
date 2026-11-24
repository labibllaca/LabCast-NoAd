package com.example.player

import com.example.data.PodcastChapter

internal data class SponsorChapterSkipTarget(val targetMs: Long) {
    companion object {
        fun at(positionMs: Long, chapters: List<PodcastChapter>): SponsorChapterSkipTarget? {
            val currentSeconds = positionMs / 1000L
            val activeIndex = chapters.indexOfLast { it.startTimeSeconds <= currentSeconds }
            if (activeIndex !in chapters.indices) return null

            val activeChapter = chapters[activeIndex]
            if (!activeChapter.isSponsorChapter()) return null

            val chapterEndSeconds = activeChapter.startTimeSeconds +
                (activeChapter.durationSeconds ?: 60L)
            val nextNonSponsorStart = chapters.drop(activeIndex + 1)
                .firstOrNull { !it.isSponsorChapter() }
                ?.startTimeSeconds
            val targetSeconds = nextNonSponsorStart ?: chapterEndSeconds
            return SponsorChapterSkipTarget(targetSeconds * 1000L)
        }
    }
}
