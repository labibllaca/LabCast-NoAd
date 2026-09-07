package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object EpisodeDownloader {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun downloadToFile(
        context: Context,
        url: String,
        episodeId: String,
        onProgress: (Float) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "podcasts")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val cleanId = episodeId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val destinationFile = File(dir, "${cleanId}.mp3")
        val tempFile = File(dir, "${cleanId}_tmp_${System.currentTimeMillis()}.mp3")

        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Log.e("EpisodeDownloader", "Invalid non-HTTP download URL: $url")
                return@withContext null
            }

            Log.i("EpisodeDownloader", "Starting real offline download from: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) LabCast/1.0")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("EpisodeDownloader", "Download failed with HTTP ${response.code}: ${response.message}")
                return@withContext null
            }

            val body = response.body ?: run {
                Log.e("EpisodeDownloader", "Response body is null")
                return@withContext null
            }

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(16384)
            var bytesRead: Int
            var totalBytes: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                if (contentLength > 0) {
                    onProgress((totalBytes.toFloat() / contentLength).coerceIn(0f, 1f))
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Verify that we downloaded a valid non-empty audio file (at least 15KB)
            if (totalBytes < 15000L || !tempFile.exists() || tempFile.length() < 15000L) {
                Log.e("EpisodeDownloader", "Downloaded file too small ($totalBytes bytes), discarding.")
                if (tempFile.exists()) tempFile.delete()
                return@withContext null
            }

            // Atomically replace destination file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            val renamed = tempFile.renameTo(destinationFile)
            if (renamed && destinationFile.exists()) {
                Log.i("EpisodeDownloader", "Successfully downloaded episode (${destinationFile.length()} bytes) to: ${destinationFile.absolutePath}")
                onProgress(1.0f)
                return@withContext destinationFile.absolutePath
            } else {
                Log.e("EpisodeDownloader", "Failed to rename temp file to destination")
                if (tempFile.exists()) tempFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e("EpisodeDownloader", "Exception during download: ${e.message}", e)
            if (tempFile.exists()) {
                try { tempFile.delete() } catch (_: Exception) {}
            }
            null
        }
    }

    fun isValidDownloadedFile(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        return try {
            val file = File(path)
            file.exists() && file.length() >= 15000L
        } catch (_: Exception) {
            false
        }
    }

    fun deleteDownloadedFile(path: String?) {
        if (path.isNullOrEmpty()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
