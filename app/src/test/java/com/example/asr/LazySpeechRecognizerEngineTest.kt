package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class LazySpeechRecognizerEngineTest {

    @Test
    fun `creates the delegate once and closes it`() {
        var created = 0
        var closed = 0
        val lazy = LazySpeechRecognizerEngine {
            created++
            object : SpeechRecognizerEngine {
                override fun startStream(startMs: Long): SpeechStream = object : SpeechStream {
                    override fun accept(samples: FloatArray) = Unit
                    override fun pollSegments(): List<AsrSegment> = emptyList()
                    override fun finish(): List<AsrSegment> = emptyList()
                    override fun close() = Unit
                }
                override fun close() { closed++ }
            }
        }

        lazy.startStream(0)
        lazy.startStream(10_000)
        assertEquals(1, created)

        lazy.close()
        assertEquals(1, closed)
    }
}
