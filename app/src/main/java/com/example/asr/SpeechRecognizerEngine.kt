package com.example.asr

/**
 * Thin boundary around the speech recognizer so the scan controller can be
 * tested without native code.
 */
interface SpeechRecognizerEngine {
    /** Starts a stream whose timestamps are relative to [startMs] on the episode timeline. */
    fun startStream(startMs: Long = 0L): SpeechStream
    fun close()
}

interface SpeechStream {
    fun accept(samples: FloatArray)
    fun pollSegments(): List<AsrSegment>
    fun finish(): List<AsrSegment>
    fun close()
}
