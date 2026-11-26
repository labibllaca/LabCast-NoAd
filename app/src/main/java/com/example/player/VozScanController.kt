package com.example.player

import android.content.Context
import com.example.data.AsrWindowEntity
import com.example.data.AsrWindowStore
import com.example.data.EpisodeEntity
import com.example.data.ManualAdRangeCodec
import com.example.util.EpisodeDownloader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Status published after each window so the UI can fill in progressively. */
data class ScanProgress(
    val completed: Int,
    val total: Int,
    val wordCount: Int,
    val cueCount: Int,
    val text: String = ""
)

/**
 * Transcribes a few predicted windows of the playing episode with the real Voz
 * SDK, reuses completed windows from the cache, and publishes results as they
 * arrive. Nothing here waits on playback or on the model download.
 */
class VozScanController(
    private val context: Context,
    private val store: AsrWindowStore,
    private val engineFactory: (Context) -> VozEngine = { VozSdkEngine(it) },
    private val reader: AudioWindowReader = VozAudioDecoder(),
    private val pathResolver: suspend (EpisodeEntity) -> String? = { episode ->
        val local = episode.downloadLocalPath
        if (episode.isDownloaded && local != null && EpisodeDownloader.isValidDownloadedFile(local)) {
            local
        } else {
            EpisodeDownloader.downloadToFile(context, episode.audioUrl, episode.id, {}, "asr-cache")
        }
    }
) {
    suspend fun scan(
        episode: EpisodeEntity,
        siblings: List<EpisodeEntity>,
        onProgress: (ScanProgress) -> Unit
    ): VozTranscriptResult {
        val durationMs = episode.durationSeconds * 1_000L
        val patternEngine = PodcastAdPatternEngine()
        val template = patternEngine.learnShowAdPattern(episode.podcastId, siblings)
        val candidates = mutableListOf<LongRange>()
        patternEngine.predictEpisodeAdSkips(episode, template).forEach { candidates.add(it.startMs..it.endMs) }
        PodcastKeywordSpotter().parseAdIntervalsFromTranscript(episode.transcript)
            .forEach { candidates.add(it.startMs..it.endMs) }
        ManualAdRangeCodec.decode(episode.manualAdRanges).forEach { candidates.add(it.startMs..it.endMs) }

        val windows = VozScanPlanner.plan(durationMs, candidates)
        if (windows.isEmpty()) return VozTranscriptResult(episode.id, sentences = emptyList(), skipRanges = emptyList())

        val path = pathResolver(episode) ?: return VozTranscriptResult(episode.id, sentences = emptyList(), skipRanges = emptyList())
        val identity = reader.audioIdentity(path)
        val completed = store.load(episode.id).filter { window ->
            window.modelVersion == MODEL_VERSION && window.audioIdentity == identity
        }
        val todo = windows.filterNot { window ->
            completed.any { it.startMs <= window.startMs && it.endMs >= window.endMs }
        }
        if (todo.isEmpty()) return VozTranscriptResult(episode.id, sentences = emptyList(), skipRanges = emptyList())

        val sentences = mutableListOf<SentenceSegment>()
        val skips = mutableListOf<SkipRange>()
        var wordCount = 0
        var cueCount = 0
        var accumulatedText = ""

        engineFactory(context).use { engine ->
            if (!engine.isDownloaded()) engine.download()
            todo.forEachIndexed { index, window ->
                val samples = reader.readWindow(path, window.startMs, window.durationMs)
                if (samples.isEmpty()) return@forEachIndexed

                val transcription = engine.transcribe(samples, SAMPLE_RATE)
                val offsetSeconds = window.startMs / 1_000.0
                val words = transcription.words.map { word ->
                    TimedWord(
                        text = word.text,
                        startMs = ((word.start + offsetSeconds) * 1_000.0).toLong(),
                        endMs = ((word.end + offsetSeconds) * 1_000.0).toLong()
                    )
                }
                wordCount += words.size
                val windowSkips = VozCueDetector.detect(words)
                cueCount += windowSkips.size
                skips.addAll(windowSkips)
                sentences.addAll(toSentences(words, windowSkips))
                store.save(
                    listOf(
                        AsrWindowEntity(
                            episodeId = episode.id,
                            startMs = window.startMs,
                            endMs = window.endMs,
                            wordsJson = wordsAdapter.toJson(words),
                            modelVersion = MODEL_VERSION,
                            audioIdentity = identity
                        )
                    )
                )
                accumulatedText += if (accumulatedText.isEmpty()) "" else " "
                accumulatedText += words.joinToString(" ") { it.text }
                onProgress(ScanProgress(index + 1, todo.size, wordCount, cueCount, accumulatedText))
            }
        }
        return VozTranscriptResult(episode.id, sentences = sentences, skipRanges = skips)
    }

    private fun toSentences(words: List<TimedWord>, skips: List<SkipRange>): List<SentenceSegment> {
        val groups = mutableListOf<List<TimedWord>>()
        var current = mutableListOf<TimedWord>()
        words.forEach { word ->
            val previous = current.lastOrNull()
            if (previous != null && (word.startMs - previous.endMs > 600L || current.size >= 12)) {
                groups.add(current.toList())
                current = mutableListOf()
            }
            current.add(word)
        }
        if (current.isNotEmpty()) groups.add(current.toList())

        return groups.map { group ->
            val start = group.first().startMs
            val end = group.last().endMs
            SentenceSegment(
                speaker = "Speaker",
                text = group.joinToString(" ") { it.text },
                startMs = start,
                endMs = end,
                isSponsor = skips.any { it.startMs <= start && it.endMs >= end },
                words = group.map { WordTimestamp(it.text, it.startMs, it.endMs) }
            )
        }
    }

    companion object {
        private const val MODEL_VERSION = "6.0.0"
        private const val SAMPLE_RATE = 16_000.0
        private val wordsAdapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter<List<TimedWord>>(
                Types.newParameterizedType(List::class.java, TimedWord::class.java)
            )
    }
}
