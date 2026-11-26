package com.example.player

import ai.desertant.voz.Transcription
import ai.desertant.voz.Word
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AsrWindowEntity
import com.example.data.AsrWindowStore
import com.example.data.EpisodeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VozScanControllerTest {
    private val episode = EpisodeEntity(
        id = "e1",
        podcastId = "p1",
        podcastTitle = "Podcast",
        podcastCoverUrl = "",
        title = "Episode",
        description = "",
        durationSeconds = 3_600L,
        publishDate = "2026-01-01",
        audioUrl = "https://example.invalid/e1.mp3",
        isDownloaded = true,
        downloadLocalPath = "/tmp/e1.mp3"
    )

    private class FakeEngine(var downloaded: Boolean = true) : VozEngine {
        val calls = mutableListOf<FloatArray>()
        var downloadCalls = 0

        override fun isDownloaded(): Boolean = downloaded

        override suspend fun download() {
            downloadCalls++
            downloaded = true
        }

        override suspend fun transcribe(samples: FloatArray, sampleRate: Double): Transcription {
            calls.add(samples)
            return Transcription(
                "Sponsored by NordVPN and now back to the show",
                listOf(
                    Word("Sponsored by", 1.0, 1.9),
                    Word("NordVPN", 2.0, 2.9),
                    Word("and now back to the show", 3.0, 4.0)
                ),
                5.0
            )
        }

        override fun close() = Unit
    }

    private class FakeReader(var failFirstWindow: Boolean = false) : AudioWindowReader {
        private var calls = 0

        override fun readWindow(path: String, startMs: Long, lengthMs: Long): FloatArray {
            calls++
            if (failFirstWindow && calls == 1) throw IllegalStateException("decode failed")
            return FloatArray(100) { 0.1f }
        }

        override fun audioIdentity(path: String): String = "100:200"
    }

    private class FakeStore(initial: List<AsrWindowEntity> = emptyList()) : AsrWindowStore {
        val rows = initial.toMutableList()

        override suspend fun load(episodeId: String): List<AsrWindowEntity> =
            rows.filter { it.episodeId == episodeId }

        override suspend fun save(windows: List<AsrWindowEntity>) {
            rows.addAll(windows)
        }
    }

    private fun controller(engine: VozEngine, store: AsrWindowStore = FakeStore()) = VozScanController(
        context = ApplicationProvider.getApplicationContext<Context>(),
        store = store,
        engineFactory = { engine },
        reader = FakeReader(),
        pathResolver = { "/tmp/e1.mp3" }
    )

    @Test
    fun downloadsModelOnce() = runBlocking {
        val engine = FakeEngine(downloaded = false)
        controller(engine).scan(episode, emptyList()) {}
        assertEquals(1, engine.downloadCalls)
    }

    @Test
    fun wordTimesAreOffsetByWindowStart() = runBlocking {
        val result = controller(FakeEngine()).scan(episode, emptyList()) {}
        assertTrue(result.sentences.any { sentence ->
            sentence.words.any { word -> word.startMs >= 570_000L }
        })
    }

    @Test
    fun coveredWindowsAreNotRescanned() = runBlocking {
        val full = FakeEngine()
        controller(full, FakeStore()).scan(episode, emptyList()) {}

        val store = FakeStore(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "6.0.0", "100:200")))
        val partial = FakeEngine()
        controller(partial, store).scan(episode, emptyList()) {}

        assertEquals(full.calls.size - 1, partial.calls.size)
    }

    @Test
    fun staleCachedWindowsAreRescanned() = runBlocking {
        val full = FakeEngine()
        controller(full, FakeStore()).scan(episode, emptyList()) {}

        val stale = FakeStore(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "5.0.0", "100:200")))
        val engine = FakeEngine()
        controller(engine, stale).scan(episode, emptyList()) {}

        assertEquals(full.calls.size, engine.calls.size)
    }

    @Test
    fun cachedWordsAreReplayedAsSkipRanges() = runBlocking {
        val wordsJson = "[{\"text\":\"Sponsored by\",\"startMs\":1000,\"endMs\":1900}," +
            "{\"text\":\"and now back to the show\",\"startMs\":2000,\"endMs\":2900}]"
        val store = FakeStore(listOf(AsrWindowEntity("e1", 0L, 60_000L, wordsJson, "6.0.0", "100:200")))
        val result = controller(FakeEngine(), store).scan(episode, emptyList()) {}
        assertTrue(result.skipRanges.isNotEmpty())
    }

    @Test
    fun audioIdentityMismatchForcesRescan() = runBlocking {
        val full = FakeEngine()
        controller(full, FakeStore()).scan(episode, emptyList()) {}

        val mismatched = FakeStore(
            listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "6.0.0", "999:999"))
        )
        val engine = FakeEngine()
        controller(engine, mismatched).scan(episode, emptyList()) {}

        assertEquals(full.calls.size, engine.calls.size)
    }

    @Test
    fun overlappingWordsAreDeduplicated() {
        val words = listOf(
            TimedWord("chapter", 57_000L, 57_900L),
            TimedWord("chapter", 57_200L, 58_000L),
            TimedWord("next", 58_500L, 59_000L)
        )
        assertEquals(2, VozScanController.dedupeWords(words).size)
    }

    @Test
    fun progressCarriesPartialSkipRanges() = runBlocking {
        val published = mutableListOf<ScanProgress>()
        controller(FakeEngine()).scan(episode, emptyList()) { published.add(it) }
        assertTrue(published.any { it.skipRanges.isNotEmpty() })
    }

    @Test
    fun cachedAsrFileIsReusedInsteadOfDownloading() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = java.io.File(context.filesDir, "asr-cache")
        dir.mkdirs()
        java.io.File(dir, "e1.mp3").writeBytes(ByteArray(15_001))
        val streamed = episode.copy(isDownloaded = false, downloadLocalPath = null)

        val store = FakeStore(listOf(AsrWindowEntity("e1", 0L, 60_000L, "[]", "6.0.0", "100:200")))
        val reused = FakeEngine()
        VozScanController(context, store, { reused }, FakeReader()).scan(streamed, emptyList()) {}

        val full = FakeEngine()
        VozScanController(context, FakeStore(), { full }, FakeReader()).scan(streamed, emptyList()) {}
        assertEquals(full.calls.size - 1, reused.calls.size)
    }

    @Test
    fun badWindowDoesNotAbortTheScan() = runBlocking {
        val engine = FakeEngine()
        val reader = FakeReader(failFirstWindow = true)
        val result = VozScanController(
            context = ApplicationProvider.getApplicationContext<Context>(),
            store = FakeStore(),
            engineFactory = { engine },
            reader = reader,
            pathResolver = { "/tmp/e1.mp3" }
        ).scan(episode, emptyList()) {}
        assertTrue(engine.calls.size > 1)
        assertTrue(result.sentences.isNotEmpty())
    }

    @Test
    fun missingAudioPathProducesEmptyResult() = runBlocking {
        val controller = VozScanController(
            context = ApplicationProvider.getApplicationContext<Context>(),
            store = FakeStore(),
            engineFactory = { FakeEngine() },
            reader = FakeReader(),
            pathResolver = { null }
        )
        val result = controller.scan(episode, emptyList()) {}
        assertTrue(result.sentences.isEmpty())
        assertTrue(result.skipRanges.isEmpty())
    }
}
