package com.example.asr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Downloads and verifies the pinned English ASR model files.
 *
 * Files are written to `<name>.part`, hashed while streaming, and only renamed
 * to their final name after size and SHA-256 match. A failed download never
 * leaves a partial file behind that the recognizer could try to load.
 */
class ModelDownloadManager internal constructor(
    private val rootDir: File,
    private val client: OkHttpClient,
    private val registry: List<AsrModelFile>,
) {

    constructor(
        context: Context,
        client: OkHttpClient = OkHttpClient(),
        registry: List<AsrModelFile> = AsrModelRegistry.files,
    ) : this(File(context.filesDir, "models"), client, registry)

    /** Test-only constructor with an explicit directory. */
    constructor(rootDir: File, registry: List<AsrModelFile>) : this(rootDir, OkHttpClient(), registry)

    @Volatile
    private var installedCache: Boolean? = null

    fun modelDir(): File = File(rootDir, AsrModelRegistry.modelDirName)

    fun isInstalled(): Boolean {
        installedCache?.let { return it }
        val installed = registry.all { file ->
            val target = File(modelDir(), file.name)
            target.isFile && target.length() == file.sizeBytes && sha256(target) == file.sha256
        }
        installedCache = installed
        return installed
    }

    suspend fun download(progress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        val totalBytes = registry.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var written = 0L
        try {
            val dir = modelDir()
            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext Result.failure(IllegalStateException("Cannot create ${dir.absolutePath}"))
            }

            for (file in registry) {
                val target = File(dir, file.name)
                if (target.isFile && target.length() == file.sizeBytes && sha256(target) == file.sha256) {
                    written += file.sizeBytes
                    progress((written.toFloat() / totalBytes).coerceIn(0f, 1f))
                    continue
                }

                val part = File(dir, "${file.name}.part")
                if (part.exists()) part.delete()

                val request = Request.Builder().url(file.url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("HTTP ${response.code} for ${file.name}")
                        )
                    }
                    val body = response.body ?: return@withContext Result.failure(
                        IllegalStateException("Empty body for ${file.name}")
                    )
                    val digest = MessageDigest.getInstance("SHA-256")
                    var fileBytes = 0L
                    FileOutputStream(part).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                fileBytes += read
                                written += read
                                progress((written.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }

                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (fileBytes != file.sizeBytes || actualHash != file.sha256) {
                        part.delete()
                        return@withContext Result.failure(
                            IllegalStateException(
                                "Verification failed for ${file.name}: " +
                                    "size=$fileBytes/${file.sizeBytes} sha=$actualHash"
                            )
                        )
                    }

                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) {
                        part.delete()
                        return@withContext Result.failure(
                            IllegalStateException("Cannot install ${file.name}")
                        )
                    }
                }
            }

            installedCache = true
            progress(1f)
            Result.success(modelDir())
        } catch (e: Exception) {
            registry.forEach { File(modelDir(), "${it.name}.part").delete() }
            installedCache = false
            Result.failure(e)
        }
    }

    fun delete() {
        modelDir().deleteRecursively()
        installedCache = false
    }

    /** Removes model directories left behind by earlier pinned versions. */
    fun deleteLegacy() {
        rootDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory &&
                entry.name.startsWith("zipformer-") &&
                entry.name != AsrModelRegistry.modelDirName
            ) {
                entry.deleteRecursively()
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
