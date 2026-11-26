package com.example.player

import android.content.Context
import android.util.Log
import com.example.data.AsrWindowEntity
import com.example.data.AsrWindowStore
import com.example.data.EpisodeEntity
import com.example.data.ManualAdRangeCodec
import com.example.util.EpisodeDownloader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File

/** Status published after each window so the UI can fill in progressively. */
data class ScanProgress(
    val completed: Int,
    val total: Int,
    val wordCount: Int,
    val cueCount: Int,
    val text: String = "",
    val sentences: List<SentenceSegment> = emptyList(),
    val skipRanges: List<SkipRange> = emptyList()
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
        when {
            episode.isDownloaded && local != null && EpisodeDownloader.isValidDownloadedFile(local) -> local
            else -> {
                val cleanId = episode.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val cached = File(context.filesDir, "asr-cache/$cleanId.mp3").absolutePath
                if (EpisodeDownloader.isValidDownloadedFile(cached)) {
                    cached
                } else {
                    EpisodeDownloader.downloadToFile(context, episode.audioUrl, episode.id, {}, "asr-cache")
                }
            }
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
        if (windows.isEmpty()) return emptyResult(episode.id)

        val path = pathResolver(episode) ?: return emptyResult(episode.id)
        val identity = reader.audioIdentity(path)
        val completed = store.load(episode.id).filter { window ->
            window.modelVersion == MODEL_VERSION && window.audioIdentity == identity
        }
        val todo = windows.filterNot { window ->
            completed.any { it.startMs <= window.startMs && it.endMs >= window.endMs }
        }

        val allWords = completed.flatMap { window ->
            try {
                wordsAdapter.fromJson(window.wordsJson).orEmpty()
            } catch (error: Exception) {
                Log.e(TAG, "Voz cache window ${window.startMs} is unreadable", error)
                emptyList()
            }
        }.toMutableList()

        var latest = emptyResult(episode.id)

        fun publish(done: Int) {
            val unique = dedupeWords(allWords)
            val skips = VozCueDetector.detect(unique)
            val sentences = toSentences(unique, skips)
            latest = VozTranscriptResult(episode.id, sentences = sentences, skipRanges = skips)
            onProgress(
                ScanProgress(
                    completed = done,
                    total = windows.size,
                    wordCount = unique.size,
                    cueCount = skips.size,
                    text = unique.joinToString(" ") { it.text },
                    sentences = sentences,
                    skipRanges = skips
                )
            )
        }

        // Cached words are replayed immediately; they are the first usable cues.
        publish(completed.size)

        if (todo.isNotEmpty()) {
            engineFactory(context).use { engine ->
                try {
                    if (!engine.isDownloaded()) engine.download()
                } catch (error: Exception) {
                    Log.e(TAG, "Voz model download failed", error)
                    return@use
                }

                todo.forEachIndexed { index, window ->
                    if (!currentCoroutineContext().isActive) return@forEachIndexed
                    val newWords = try {
                        val samples = reader.readWindow(path, window.startMs, window.durationMs)
                        if (samples.isEmpty()) emptyList()
                        else {
                            val transcription = engine.transcribe(samples, SAMPLE_RATE)
                            val offsetSeconds = window.startMs / 1_000.0
                            transcription.words.map { word ->
                                TimedWord(
                                    text = word.text,
                                    startMs = ((word.start + offsetSeconds) * 1_000.0).toLong(),
                                    endMs = ((word.end + offsetSeconds) * 1_000.0).toLong()
                                )
                            }
                        }
                    } catch (error: Exception) {
                        Log.e(TAG, "Voz window ${window.startMs} failed", error)
                        emptyList()
                    }

                    if (newWords.isNotEmpty()) {
                        allWords.addAll(newWords)
                        try {
                            store.save(
                                listOf(
                                    AsrWindowEntity(
                                        episodeId = episode.id,
                                        startMs = window.startMs,
                                        endMs = window.endMs,
                                        wordsJson = wordsAdapter.toJson(newWords),
                                        modelVersion = MODEL_VERSION,
                                        audioIdentity = identity
                                    )
                                )
                            )
                        } catch (error: Exception) {
                            Log.e(TAG, "Voz cache save failed for ${window.startMs}", error)
                        }
                    }
                    publish(completed.size + index + 1)
                }
            }
        }
        return latest
    }

    private fun emptyResult(episodeId: String) = VozTranscriptResult(
        episodeId,
        sentences = emptyList(),
        skipRanges = emptyList()
    )

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
        private const val TAG = "VozScanController"
        private const val MODEL_VERSION = "6.0.0"
        private const val SAMPLE_RATE = 16_000.0
        private val wordsAdapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter<List<TimedWord>>(
                Types.newParameterizedType(List::class.java, TimedWord::class.java)
            )

        /** Keeps one copy of a word heard in both overlapping windows. */
        fun dedupeWords(words: List<TimedWord>): List<TimedWord> {
            val sorted = words.sortedWith(compareBy<TimedWord> { it.startMs }.thenBy { it.endMs })
            val unique = mutableListOf<TimedWord>()
            sorted.forEach { word ->
                val previous = unique.lastOrNull()
                if (previous == null || previous.text != word.text || word.startMs - previous.startMs > 300L) {
                    unique.add(word)
                }
            }
            return unique
        }
    }
}
