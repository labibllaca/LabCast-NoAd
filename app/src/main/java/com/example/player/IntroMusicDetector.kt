package com.example.player

import com.example.data.ChapterParser
import com.example.data.TranscriptParser
import java.util.Locale

data class IntroMusicInterval(
    val startMs: Long = 0L,
    val endMs: Long,
    val podcastTitle: String,
    val reason: String = "Theme music jingle detected",
) {
    val durationSeconds: Long get() = endMs / 1000
}

/**
 * Finds an intro-music chapter or a transcript cue that says the intro ends
 * where speech begins. No per-show hard-coded fallback: without evidence the
 * app does not skip the intro.
 */
class IntroMusicDetector {

    fun detect(
        podcastTitle: String,
        transcript: String? = null,
        chapters: String? = null,
    ): IntroMusicInterval? {
        if (!chapters.isNullOrBlank()) {
            val parsed = ChapterParser.parseChapters(chapters, null, 0L)
            val index = parsed.indexOfFirst { chapter ->
                val title = chapter.title.lowercase(Locale.ROOT)
                title.contains("intro") &&
                    (title.contains("music") || title.contains("theme") || title.contains("jingle"))
            }
            if (index >= 0) {
                val endMs = parsed.getOrNull(index + 1)?.startTimeSeconds?.times(1000L)
                    ?: 28_000L
                return IntroMusicInterval(
                    endMs = endMs,
                    podcastTitle = podcastTitle,
                    reason = "Chapter mark: ${parsed[index].title}",
                )
            }
        }

        if (!transcript.isNullOrBlank()) {
            val segments = TranscriptParser.parseTranscript(transcript, 0L)
            val index = segments.indexOfFirst { segment ->
                INTRO_MUSIC_PATTERNS.any { segment.text.contains(it, ignoreCase = true) }
            }
            if (index >= 0) {
                val endMs = segments.getOrNull(index + 1)?.startTimeSeconds?.times(1000L)
                    ?: 28_000L
                return IntroMusicInterval(
                    endMs = endMs,
                    podcastTitle = podcastTitle,
                    reason = "Transcript cue: intro music ends at ${endMs / 1000}s",
                )
            }
        }

        return null
    }

    private companion object {
        val INTRO_MUSIC_PATTERNS = listOf(
            "intro music",
            "theme music",
            "theme song",
            "intro jingle",
            "jingle",
        )
    }
}
