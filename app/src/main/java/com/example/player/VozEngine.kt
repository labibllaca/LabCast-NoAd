package com.example.player

import ai.desertant.voz.Transcription
import ai.desertant.voz.Voz
import android.content.Context

/** The Desert Ant Voz calls the scanner needs, kept behind an interface for tests. */
interface VozEngine : AutoCloseable {
    fun isDownloaded(): Boolean
    suspend fun download()
    suspend fun transcribe(samples: FloatArray, sampleRate: Double = 16_000.0): Transcription
}

/** The real SDK implementation. One instance is created and reused per scan session. */
class VozSdkEngine(context: Context) : VozEngine {
    private val voz = Voz(context)

    override fun isDownloaded(): Boolean = voz.isDownloaded()

    override suspend fun download() = voz.download()

    override suspend fun transcribe(samples: FloatArray, sampleRate: Double): Transcription =
        voz.transcribe(samples, sampleRate)

    override fun close() = voz.close()
}
