package com.example.asr

import com.example.data.EpisodeEntity
import java.io.Closeable

/** Produces 16 kHz mono PCM for arbitrary episode ranges. */
interface PcmSource {
    val durationMs: Long
    fun reader(startMs: Long): PcmReader
}

interface PcmReader : Closeable {
    /** Fills [out] and returns the number of samples written, or 0 at the end. */
    fun read(out: FloatArray): Int
}

fun interface PcmSourceFactory {
    fun create(episode: EpisodeEntity): PcmSource
}
