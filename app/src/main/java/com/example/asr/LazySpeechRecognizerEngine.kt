package com.example.asr

/**
 * Defers loading the native sherpa-onnx recognizer until the first scan, so
 * the app starts and plays audio even when the model is not downloaded yet.
 */
class LazySpeechRecognizerEngine(
    private val factory: () -> SpeechRecognizerEngine,
) : SpeechRecognizerEngine {

    private var delegate: SpeechRecognizerEngine? = null

    override fun startStream(startMs: Long): SpeechStream {
        val engine = delegate ?: factory().also { delegate = it }
        return engine.startStream(startMs)
    }

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
