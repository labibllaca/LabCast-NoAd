package com.example.asr

/**
 * A single model file that is downloaded on first enable.
 *
 * The SHA-256 and byte size are pinned so a changed or truncated download is
 * rejected instead of being fed to the recognizer.
 */
data class AsrModelFile(
    val name: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
)

/**
 * Pinned English-only model set for on-device ad detection.
 *
 * Runtime: sherpa-onnx streaming Zipformer (English, int8).
 * Model: csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17 (Apache-2.0).
 * VAD: Silero VAD (Apache-2.0).
 */
object AsrModelRegistry {
    const val modelDirName = "zipformer-en-20m-2023-02-17"

    private const val BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17/resolve/main"

    val files = listOf(
        AsrModelFile(
            name = "encoder-epoch-99-avg-1.int8.onnx",
            url = "$BASE/encoder-epoch-99-avg-1.int8.onnx",
            sha256 = "3810755ce7c3ab26b42a8bcf39d191308fa27fb0f53358823ba46141d03b7eb3",
            sizeBytes = 42_845_182L,
        ),
        AsrModelFile(
            name = "decoder-epoch-99-avg-1.int8.onnx",
            url = "$BASE/decoder-epoch-99-avg-1.int8.onnx",
            sha256 = "21e2a2acd961b3ac72f55be2f10f1a285e1b0b0ba010d7c0b6eab141411b163c",
            sizeBytes = 539_499L,
        ),
        AsrModelFile(
            name = "joiner-epoch-99-avg-1.int8.onnx",
            url = "$BASE/joiner-epoch-99-avg-1.int8.onnx",
            sha256 = "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e",
            sizeBytes = 259_572L,
        ),
        AsrModelFile(
            name = "tokens.txt",
            url = "$BASE/tokens.txt",
            sha256 = "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb",
            sizeBytes = 5_048L,
        ),
        AsrModelFile(
            name = "silero_vad.onnx",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
            sha256 = "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
            sizeBytes = 643_854L,
        ),
    )

    val totalBytes: Long = files.sumOf { it.sizeBytes }
}
