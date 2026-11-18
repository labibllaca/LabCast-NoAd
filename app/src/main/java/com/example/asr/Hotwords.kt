package com.example.asr

import java.util.Locale

/**
 * Builds the contextual-biasing string handed to the transducer per stream.
 *
 * sherpa-onnx treats each line as one phrase to boost; the model tokenizes it
 * with its own BPE table. Scores are global (`hotwordsScore`), so lines carry
 * no per-phrase score.
 */
object Hotwords {
    const val MAX_LINES = 100

    fun build(brands: List<String>, cues: List<String>): String =
        (brands + cues)
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_LINES)
            .joinToString("\n")
}
