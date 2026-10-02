package com.example.player

import java.util.Locale
import kotlin.math.max

/**
 * AdSkipInterval represents a detected advertisement segment identified through
 * Approach A (Transcript parsing), Approach B (Acoustic RMS waveform dynamics),
 * or Approach C (Lightweight on-device keyword spotting).
 */
data class AdSkipInterval(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val triggerWord: String,
    val resumeWord: String,
    val confidence: Float, // 0.0 to 1.0
    val source: String, // e.g. "Approach A (Transcript)", "Approach B (Acoustic RMS)", "Approach C (Keyword Spotter)"
    val description: String = "Ad from '$triggerWord' to '$resumeWord'"
) {
    val durationMs: Long get() = max(0L, endMs - startMs)
    val durationSeconds: Long get() = durationMs / 1000
}

/**
 * IntroMusicInterval represents a detected musical theme or jingle that plays
 * before the host begins speaking (e.g., Art of Manliness intro music).
 */
data class IntroMusicInterval(
    val startMs: Long = 0L,
    val endMs: Long,
    val podcastTitle: String,
    val reason: String = "Theme music jingle detected"
) {
    val durationSeconds: Long get() = endMs / 1000
}

/**
 * PodcastKeywordSpotter:
 * High-performance, battery-friendly keyword spotting & transcript analysis engine.
 * Combines:
 * - Approach A: Exact timestamped transcript line parsing ("Sponsors" -> "and now back to the show")
 * - Approach B: Audio volume and dynamic range correlation
 * - Approach C: On-device sliding-window keyword spotting
 */
class PodcastKeywordSpotter {

    companion object {
        val SPONSOR_START_PATTERNS = listOf(
            "sponsors",
            "sponsor",
            "sponsored by",
            "brought to you by",
            "support for this podcast",
            "a word from our sponsor",
            "our partners today",
            "partner break"
        )

        val SPONSOR_RESUME_PATTERNS = listOf(
            "and now back to the show",
            "back to the show",
            "back to our conversation",
            "back to the podcast",
            "welcome back",
            "now back to",
            "let's get back to"
        )

        val INTRO_MUSIC_PATTERNS = listOf(
            "intro music",
            "theme music",
            "theme song",
            "electric guitar",
            "jingle",
            "intro jingle"
        )
    }

    /**
     * Phase 2: Detects intro music/jingle duration for podcasts like "The Art of Manliness" (AoM).
     * If the podcast is AoM or has an intro jingle in chapters/transcripts, returns the skip duration in ms.
     */
    fun detectIntroMusic(
        podcastTitle: String,
        transcript: String? = null,
        chapters: String? = null
    ): IntroMusicInterval? {
        val isAoM = podcastTitle.contains("Art of Manliness", ignoreCase = true) ||
                podcastTitle.contains("AoM", ignoreCase = true)

        // 1. Check chapter marks for intro
        if (!chapters.isNullOrBlank()) {
            val chapterEntries = chapters.split("|").map { it.trim() }
            val introChapter = chapterEntries.firstOrNull { entry ->
                entry.contains("Intro", ignoreCase = true) && entry.contains("Music", ignoreCase = true)
            }
            if (introChapter != null) {
                // Find next chapter start
                val nextChapter = chapterEntries.getOrNull(1)
                val nextSec = nextChapter?.substringBefore(":")?.toLongOrNull() ?: 28L
                return IntroMusicInterval(
                    endMs = nextSec * 1000L,
                    podcastTitle = podcastTitle,
                    reason = "Chapter mark: $introChapter"
                )
            }
        }

        // 2. Check transcript for intro music cues
        if (!transcript.isNullOrBlank()) {
            val lines = transcript.split("\n")
            for (i in lines.indices) {
                val line = lines[i]
                if (INTRO_MUSIC_PATTERNS.any { line.contains(it, ignoreCase = true) }) {
                    val nextLine = lines.getOrNull(i + 1)
                    val nextSec = parseTimestampSeconds(nextLine ?: "") ?: 28L
                    return IntroMusicInterval(
                        endMs = nextSec * 1000L,
                        podcastTitle = podcastTitle,
                        reason = "Transcript cue: Intro Music -> Speech start at ${nextSec}s"
                    )
                }
            }
        }

        // 3. AoM signature default (AoM guitar & drum intro is 28-32 seconds)
        if (isAoM) {
            return IntroMusicInterval(
                endMs = 28_000L,
                podcastTitle = podcastTitle,
                reason = "Art of Manliness signature rock guitar & drum intro (28s)"
            )
        }

        return null
    }

    /**
     * Phase 3 & 4 (Approach A):
     * Parses timestamped transcript lines to identify exact commercial ad intervals.
     * Looks for "Sponsors" ... "And now back to the show" or standard sponsor marks.
     */
    fun parseAdIntervalsFromTranscript(transcript: String?): List<AdSkipInterval> {
        if (transcript.isNullOrBlank()) return emptyList()

        val intervals = mutableListOf<AdSkipInterval>()
        val lines = transcript.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        var currentStartSec: Long? = null
        var currentTrigger: String? = null

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            val lineSec = parseTimestampSeconds(line)

            // Detect Ad Start Cue (e.g. "Sponsors", "Brought to you by", etc.)
            val matchedStart = SPONSOR_START_PATTERNS.firstOrNull { lower.contains(it) }
            if (matchedStart != null && currentStartSec == null) {
                currentStartSec = lineSec ?: 0L
                currentTrigger = matchedStart
            }

            // Detect Ad Resume Cue (e.g. "and now back to the show")
            val matchedResume = SPONSOR_RESUME_PATTERNS.firstOrNull { lower.contains(it) }
            if (matchedResume != null && currentStartSec != null) {
                // If the resume cue is on this line, the end timestamp is either this line's timestamp + 3s or next
                val endSec = (lineSec ?: (currentStartSec + 60L)).coerceAtLeast(currentStartSec + 15L)
                intervals.add(
                    AdSkipInterval(
                        id = "ad_transcript_${currentStartSec}_$endSec",
                        startMs = currentStartSec * 1000L,
                        endMs = endSec * 1000L,
                        triggerWord = currentTrigger ?: "Sponsors",
                        resumeWord = matchedResume,
                        confidence = 0.98f,
                        source = "Approach A (Transcript)",
                        description = "Skipped ad: '$currentTrigger' -> '$matchedResume' (${endSec - currentStartSec}s)"
                    )
                )
                currentStartSec = null
                currentTrigger = null
            }
        }

        // If a sponsor block started without an explicit resume cue, assume standard 60-90s ad
        if (currentStartSec != null) {
            val fallbackEnd = currentStartSec + 60L
            intervals.add(
                AdSkipInterval(
                    id = "ad_transcript_fallback_${currentStartSec}",
                    startMs = currentStartSec * 1000L,
                    endMs = fallbackEnd * 1000L,
                    triggerWord = currentTrigger ?: "Sponsors",
                    resumeWord = "60s Ad Duration",
                    confidence = 0.85f,
                    source = "Approach A (Transcript Fallback)",
                    description = "Skipped ad: '$currentTrigger' (60s block)"
                )
            )
        }

        return intervals
    }

    /**
     * Phase 4 (Approach C):
     * Lightweight sliding-window keyword spotting across real-time speech text tokens.
     */
    fun spotLiveKeywords(windowText: String): Pair<Boolean, String?> {
        val lower = windowText.lowercase(Locale.ROOT)
        val startHit = SPONSOR_START_PATTERNS.firstOrNull { lower.contains(it) }
        if (startHit != null) {
            return Pair(true, "Trigger: '$startHit'")
        }
        val resumeHit = SPONSOR_RESUME_PATTERNS.firstOrNull { lower.contains(it) }
        if (resumeHit != null) {
            return Pair(false, "Resume: '$resumeHit'")
        }
        return Pair(false, null)
    }

    /**
     * Helper to parse timestamps formatted as "MM:SS" or "HH:MM:SS" from a line.
     */
    private fun parseTimestampSeconds(line: String): Long? {
        val regex = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?""")
        val match = regex.find(line.trim()) ?: return null

        val part1 = match.groupValues[1].toLongOrNull() ?: 0L
        val part2 = match.groupValues[2].toLongOrNull() ?: 0L
        val part3 = match.groupValues.getOrNull(3)?.toLongOrNull()

        return if (part3 != null) {
            // HH:MM:SS
            part1 * 3600 + part2 * 60 + part3
        } else {
            // MM:SS
            part1 * 60 + part2
        }
    }
}
