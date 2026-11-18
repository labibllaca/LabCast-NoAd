package com.example.asr

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.data.EpisodeEntity
import okhttp3.OkHttpClient
import java.io.File

/**
 * Decodes a downloaded file or a remote episode to 16 kHz mono PCM.
 *
 * A reader starts at the requested range, trims samples that the decoder
 * returns from the previous sync frame, and resamples to the recognizer rate.
 */
class MediaPcmSourceFactory(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val cacheDir: File,
) : PcmSourceFactory {

    override fun create(episode: EpisodeEntity): PcmSource {
        val local = episode.downloadLocalPath?.let(::File)
        return if (local != null && local.isFile) {
            MediaPcmSource { fileExtractor(local) }
        } else {
            val http = HttpRangeDataSource(episode.audioUrl, cacheDir, OkHttpClient())
            MediaPcmSource { httpExtractor(http) }
        }
    }

    private fun fileExtractor(file: File): MediaExtractor {
        return MediaExtractor().apply { setDataSource(file.absolutePath) }
    }

    private fun httpExtractor(source: HttpRangeDataSource): MediaExtractor {
        return MediaExtractor().apply { setDataSource(HttpMediaDataSource(source)) }
    }
}

private class MediaPcmSource(
    private val extractorFactory: () -> MediaExtractor,
) : PcmSource {

    override val durationMs: Long by lazy {
        val extractor = extractorFactory()
        try {
            val index = firstAudioTrack(extractor)
            if (index < 0) 0L else {
                val format = extractor.getTrackFormat(index)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION) / 1000L
                } else {
                    0L
                }
            }
        } finally {
            extractor.release()
        }
    }

    override fun reader(startMs: Long): PcmReader {
        return MediaPcmReader(extractorFactory(), startMs)
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }
}

private class MediaPcmReader(
    private val extractor: MediaExtractor,
    startMs: Long,
) : PcmReader {

    private val codec: MediaCodec
    private var sampleRate: Int
    private var channels: Int
    private var pcmEncoding: Int
    private val startUs = startMs * 1000L
    private var firstOutput = true
    private var inputDone = false
    private var outputDone = false
    private var pending = FloatArray(0)
    private var pendingOffset = 0

    init {
        val trackIndex = (0 until extractor.trackCount).firstOrNull {
            val mime = extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) ?: ""
            mime.startsWith("audio/")
        } ?: error("No audio track")

        extractor.selectTrack(trackIndex)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val format = extractor.getTrackFormat(trackIndex)
        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("No MIME type")
        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
    }

    override fun read(out: FloatArray): Int {
        if (out.isEmpty()) return 0
        if (pendingOffset < pending.size) return drain(out)

        val info = MediaCodec.BufferInfo()
        while (!outputDone) {
            feedInput()
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buffer.get(bytes)
                        val decoded = convert(bytes)
                        val trimmed = if (firstOutput) {
                            firstOutput = false
                            val skip = ((startUs - info.presentationTimeUs).coerceAtLeast(0L) *
                                sampleRate / 1_000_000L).toInt()
                            if (skip > 0 && skip < decoded.size) decoded.copyOfRange(skip, decoded.size) else decoded
                        } else {
                            decoded
                        }
                        if (trimmed.isNotEmpty()) {
                            pending = PcmConverter.resample(trimmed, sampleRate)
                            pendingOffset = 0
                            codec.releaseOutputBuffer(outIndex, false)
                            if (pending.isNotEmpty()) return drain(out)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Keep draining; EOS arrives as a buffer flag.
                }
            }
        }
        return 0
    }

    private fun drain(out: FloatArray): Int {
        val available = pending.size - pendingOffset
        val count = minOf(out.size, available)
        System.arraycopy(pending, pendingOffset, out, 0, count)
        pendingOffset += count
        return count
    }

    private fun feedInput() {
        if (inputDone) return
        val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inIndex < 0) return
        val buffer = codec.getInputBuffer(inIndex) ?: return
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun convert(bytes: ByteArray): FloatArray {
        return if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            PcmConverter.pcmFloatToFloat(bytes, channels)
        } else {
            PcmConverter.pcm16ToFloat(bytes, channels)
        }
    }

    override fun close() {
        try {
            codec.stop()
        } catch (_: Exception) {
        }
        codec.release()
        extractor.release()
        pending = FloatArray(0)
        pendingOffset = 0
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
