package com.example.player

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/** Supplies 16 kHz mono samples for a bounded slice of episode audio. */
interface AudioWindowReader {
    fun readWindow(path: String, startMs: Long, lengthMs: Long): FloatArray
    fun audioIdentity(path: String): String
}

/** Decodes a local audio file with MediaExtractor + MediaCodec. */
class VozAudioDecoder : AudioWindowReader {
    override fun readWindow(path: String, startMs: Long, lengthMs: Long): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return FloatArray(0)

            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val windowStartUs = startMs * 1_000L
            val windowEndUs = (startMs + lengthMs) * 1_000L
            val info = MediaCodec.BufferInfo()
            val chunks = mutableListOf<ShortArray>()
            var totalShorts = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: break
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex) ?: break
                        val shorts = ShortArray(buffer.asShortBuffer().remaining())
                        buffer.asShortBuffer().get(shorts)
                        if (info.presentationTimeUs >= windowStartUs) {
                            chunks.add(shorts)
                            totalShorts += shorts.size
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                            info.presentationTimeUs >= windowEndUs
                        ) {
                            outputDone = true
                        }
                    }
                }
            }

            val pcm = ShortArray(totalShorts)
            var offset = 0
            chunks.forEach { chunk ->
                System.arraycopy(chunk, 0, pcm, offset, chunk.size)
                offset += chunk.size
            }
            return PcmConverter.toMono16k(pcm, channels, sampleRate)
        } finally {
            codec?.let { decoder ->
                try {
                    decoder.stop()
                } catch (_: Exception) {
                }
                decoder.release()
            }
            extractor.release()
        }
    }

    override fun audioIdentity(path: String): String {
        val file = File(path)
        return if (file.exists()) "${file.length()}:${file.lastModified()}" else "missing"
    }
}
