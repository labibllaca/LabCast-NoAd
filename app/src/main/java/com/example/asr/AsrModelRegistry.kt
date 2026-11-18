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
 * Model: csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26 (Apache-2.0).
 * VAD: Silero VAD (Apache-2.0).
 */
object AsrModelRegistry {
    const val modelDirName = "zipformer-en-2023-06-26"

    private const val BASE =
        "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26/resolve/main"

    private const val ENCODER = "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    private const val DECODER = "decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    private const val JOINER = "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx"

    val files = listOf(
        AsrModelFile(
            name = ENCODER,
            url = "$BASE/$ENCODER",
            sha256 = "563fde436d16cf7607cf408cd6b30909819d03162652ef389c2450ced3f45ac1",
            sizeBytes = 71_083_163L,
        ),
        AsrModelFile(
            name = DECODER,
            url = "$BASE/$DECODER",
            sha256 = "98da299f471e38bb4e1a8df579b8cc9122d6039576a77e357b3c60f17dd83b02",
            sizeBytes = 1_307_236L,
        ),
        AsrModelFile(
            name = JOINER,
            url = "$BASE/$JOINER",
            sha256 = "d944208d660d67c8d72cd2acaeac971fa5ceb8c80e76c1968148846fedd6e297",
            sizeBytes = 259_335L,
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

    val encoderName: String get() = ENCODER
    val decoderName: String get() = DECODER
    val joinerName: String get() = JOINER
}
