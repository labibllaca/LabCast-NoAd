package com.example.asr

/**
 * Thin boundary around the speech recognizer so the scan controller can be
 * tested without native code.
 */
interface SpeechRecognizerEngine {
    fun startStream(): SpeechStream
    fun close()
}

interface SpeechStream {
    fun accept(samples: FloatArray)
    fun pollSegments(): List<AsrSegment>
    fun finish(): List<AsrSegment>
    fun close()
}
