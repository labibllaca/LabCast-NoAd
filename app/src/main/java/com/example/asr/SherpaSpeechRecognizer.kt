package com.example.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import kotlin.math.roundToLong

/**
 * sherpa-onnx streaming Zipformer wrapper.
 *
 * Each [startStream] gets a fresh timebase starting at zero, so the scan
 * controller can add the scan range's start time to every returned segment.
 */
class SherpaSpeechRecognizer(
    modelDir: File,
    numThreads: Int = 2,
) : SpeechRecognizerEngine {

    private val recognizer: OnlineRecognizer

    init {
        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(modelDir, "encoder-epoch-99-avg-1.int8.onnx").absolutePath,
                decoder = File(modelDir, "decoder-epoch-99-avg-1.int8.onnx").absolutePath,
                joiner = File(modelDir, "joiner-epoch-99-avg-1.int8.onnx").absolutePath,
            ),
            tokens = File(modelDir, "tokens.txt").absolutePath,
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
            modelType = "zipformer",
        )
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = modelConfig,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        recognizer = OnlineRecognizer(assetManager = null, config = config)
    }

    override fun startStream(): SpeechStream = SherpaStream(recognizer)

    override fun close() {
        recognizer.release()
    }

    private class SherpaStream(
        private val recognizer: OnlineRecognizer,
    ) : SpeechStream {
        private val stream: OnlineStream = recognizer.createStream()
        private val pending = mutableListOf<AsrSegment>()
        private var audioSamples = 0L
        private var streamStartMs = 0L
        private var finished = false
        private var closed = false

        override fun accept(samples: FloatArray) {
            if (finished || closed || samples.isEmpty()) return
            stream.acceptWaveform(samples, SAMPLE_RATE)
            audioSamples += samples.size
            decodeAndCollect()
        }

        override fun pollSegments(): List<AsrSegment> {
            if (!finished && !closed) decodeAndCollect()
            return drain()
        }

        override fun finish(): List<AsrSegment> {
            if (!finished && !closed) {
                finished = true
                stream.inputFinished()
                decodeAndCollect()
                emitCurrentIfAny()
            }
            return drain()
        }

        override fun close() {
            if (closed) return
            closed = true
            stream.release()
            pending.clear()
        }

        private fun decodeAndCollect() {
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            if (recognizer.isEndpoint(stream)) {
                val tail = FloatArray(TAIL_PADDING_SAMPLES)
                stream.acceptWaveform(tail, SAMPLE_RATE)
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }
                emitCurrentIfAny()
                recognizer.reset(stream)
                streamStartMs = audioSamples * 1000L / SAMPLE_RATE
            }
        }

        private fun emitCurrentIfAny() {
            val result = recognizer.getResult(stream)
            val text = result.text.trim()
            if (text.isEmpty() || result.timestamps.isEmpty()) return

            val start = streamStartMs + (result.timestamps.first() * 1000f).roundToLong()
            val end = streamStartMs + (result.timestamps.last() * 1000f).roundToLong() + 400L
            val confidence = if (result.ysProbs.isEmpty()) {
                0.9f
            } else {
                result.ysProbs.average().toFloat().coerceIn(0f, 1f)
            }
            pending += AsrSegment(
                startMs = start.coerceAtLeast(0L),
                endMs = end.coerceAtLeast(start),
                text = text,
                confidence = confidence,
            )
        }

        private fun drain(): List<AsrSegment> {
            if (pending.isEmpty()) return emptyList()
            val out = pending.toList()
            pending.clear()
            return out
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val TAIL_PADDING_SAMPLES = 12_800 // 0.8 s
    }
}
