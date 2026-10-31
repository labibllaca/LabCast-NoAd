package com.example.asr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SherpaRecognizerInstrumentedTest {

    @Test
    fun transcribesSampleWavWithTimestamps() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val models = ModelDownloadManager(target, OkHttpClient()).download {}.getOrThrow()
        val wav = WavReader.read(testContext.assets.open("asr_sample.wav"))

        val engine = SherpaSpeechRecognizer(models)
        try {
            val stream = engine.startStream()
            wav.samples.toList().chunked(1_600).forEach { stream.accept(it.toFloatArray()) }
            val segments = stream.finish()
            stream.close()

            assertTrue(segments.isNotEmpty())
            assertTrue(segments.joinToString(" ") { it.text }.isNotBlank())
            assertTrue(segments.all { it.endMs >= it.startMs })
        } finally {
            engine.close()
        }
    }
}
