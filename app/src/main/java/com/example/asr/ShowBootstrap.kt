package com.example.asr

import com.example.data.ChapterParser
import com.example.data.EpisodeEntity
import com.example.data.TranscriptParser

/**
 * Cheap bootstrap evidence from the feed itself: a published transcript or
 * real sponsor chapters. Candidates are never skippable until ASR confirms the
 * cue in the predicted window.
 */
class ShowBootstrap(
    private val cueDetector: AdCueDetector = AdCueDetector(),
) {

    fun candidates(episode: EpisodeEntity): List<DetectedAd> {
        val result = mutableListOf<DetectedAd>()

        val transcriptSegments = TranscriptParser.parseTranscript(
            episode.transcript.ifBlank { null },
            episode.durationSeconds,
        )
        if (transcriptSegments.isNotEmpty()) {
            val asrSegments = transcriptSegments.mapIndexed { index, segment ->
                val nextStart = transcriptSegments.getOrNull(index + 1)?.startTimeSeconds
                val endSeconds = nextStart ?: (segment.startTimeSeconds + 12L)
                AsrSegment(
                    startMs = segment.startTimeSeconds * 1000L,
                    endMs = endSeconds * 1000L,
                    text = segment.text,
                    confidence = 0.9f,
                )
            }
            cueDetector.adsFromSegments(asrSegments).forEach { ad ->
                result += ad.copy(confirmed = false, source = AdSource.TRANSCRIPT)
            }
        }

        val chapters = ChapterParser.parseChapters(
            episode.chapters,
            episode.description,
            episode.durationSeconds,
        )
        chapters.forEachIndexed { index, chapter ->
            if (!chapter.isSponsorChapter()) return@forEachIndexed
            val startMs = chapter.startTimeSeconds * 1000L
            val endMs = chapters.getOrNull(index + 1)?.startTimeSeconds?.times(1000L)
                ?: (startMs + 60_000L)
            result += DetectedAd(
                startMs = startMs,
                endMs = endMs,
                label = chapter.title,
                confidence = 0.5f,
                startCue = null,
                endCue = null,
                confirmed = false,
                source = AdSource.CHAPTER,
            )
        }

        return result
            .sortedBy { it.startMs }
            .fold(mutableListOf<DetectedAd>()) { kept, candidate ->
                val overlaps = kept.any { it.startMs < candidate.endMs && candidate.startMs < it.endMs }
                if (!overlaps) kept += candidate
                kept
            }
    }
}
