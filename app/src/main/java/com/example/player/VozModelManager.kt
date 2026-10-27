package com.example.player

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.data.EpisodeEntity
import java.nio.FloatBuffer

/**
 * Real ONNX Runtime Engine Manager for Desert Ant 'Voz' Speech/TTS Model.
 *
 * Provides ONNX Runtime local inference capability using Microsoft ONNX Runtime
 * (com.microsoft.onnxruntime:onnxruntime-android). Loads 'voz.onnx' from assets if present
 * or prepares ONNX tensor pipelines for real-time speech tokenization, sponsor audio processing,
 * and speech synthesis.
 */
object VozModelManager {

    private const val TAG = "VozModelManager"
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false

    /**
     * Initializes the ONNX Runtime Environment and creates an OrtSession for 'voz.onnx'.
     */
    fun loadVozModel(context: Context): Boolean {
        if (isModelLoaded && ortSession != null) return true

        return try {
            val env = OrtEnvironment.getEnvironment("VozTTS")
            ortEnv = env

            val assetList = context.assets.list("") ?: emptyArray()
            val hasVozAsset = assetList.contains("voz.onnx")

            if (hasVozAsset) {
                val modelBytes = context.assets.open("voz.onnx").readBytes()
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                }
                ortSession = env.createSession(modelBytes, sessionOptions)
                isModelLoaded = true
                Log.i(TAG, "✓ ONNX Runtime: 'voz.onnx' model session loaded successfully.")
                true
            } else {
                Log.i(TAG, "ONNX Runtime initialized (Environment ready). 'voz.onnx' asset location: app/src/main/assets/voz.onnx")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ONNX Runtime session: ${e.message}", e)
            false
        }
    }

    /**
     * Executes real ONNX Runtime inference over audio PCM float buffers or tokenized audio feature tensors.
     */
    fun runOnnxInference(inputData: FloatArray, tensorShape: LongArray): FloatArray? {
        val env = ortEnv ?: return null
        val session = ortSession ?: return null

        return try {
            val floatBuffer = FloatBuffer.wrap(inputData)
            val inputTensor = OnnxTensor.createTensor(env, floatBuffer, tensorShape)

            inputTensor.use { tensor ->
                val inputName = session.inputNames.iterator().next() ?: "input"
                val results = session.run(mapOf(inputName to tensor))

                results.use { outputMap ->
                    val outputValue = outputMap.iterator().next().value
                    if (outputValue is OnnxTensor) {
                        outputValue.floatBuffer.array()
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX inference error: ${e.message}", e)
            null
        }
    }

    /**
     * Generates synthesized speech PCM audio data for podcast segments or ad replacement using Voz ONNX.
     */
    fun generateSpeechWithVoz(text: String, context: Context): ByteArray? {
        loadVozModel(context)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        // Convert text tokens into float buffer
        val tokens = textBytes.map { it.toFloat() }.toFloatArray()
        val shape = longArrayOf(1, tokens.size.toLong())

        val onnxOutput = runOnnxInference(tokens, shape)
        return if (onnxOutput != null) {
            // Convert float PCM to 16-bit PCM bytes
            val pcmBytes = ByteArray(onnxOutput.size * 2)
            for (i in onnxOutput.indices) {
                val sample = (onnxOutput[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            pcmBytes
        } else {
            null
        }
    }

    fun isLoaded(): Boolean = isModelLoaded
}
