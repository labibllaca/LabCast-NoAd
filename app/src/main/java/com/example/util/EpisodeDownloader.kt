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

        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Log.e("EpisodeDownloader", "Invalid non-HTTP download URL: $url")
                return@withContext null
            }

            com.example.network.NetworkRetryPolicy.executeWithConnectionRetry(
                operationName = "Download ($cleanId)",
                maxMinuteCycles = 3
            ) {
                val partialFile = File(dir, "${cleanId}.part")
                try {
                    val existingBytes = if (partialFile.exists()) partialFile.length() else 0L
                    Log.i("EpisodeDownloader", "Starting real offline download from: $url (existingBytes=$existingBytes)")

                    val requestBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) LabCast/1.0")
                        .header("Accept", "*/*")
                        .header("Accept-Encoding", "identity")

                    if (existingBytes > 0L) {
                        requestBuilder.header("Range", "bytes=$existingBytes-")
                        Log.i("EpisodeDownloader", "Requesting HTTP Range: bytes=$existingBytes-")
                    }

                    var response = client.newCall(requestBuilder.build()).execute()

                    // If HTTP 416 (Range Not Satisfiable), delete partial file and retry fresh
                    if (response.code == 416) {
                        response.close()
                        partialFile.delete()
                        val freshRequest = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) LabCast/1.0")
                            .header("Accept", "*/*")
                            .header("Accept-Encoding", "identity")
                            .build()
                        response = client.newCall(freshRequest).execute()
                    }

                    if (!response.isSuccessful) {
                        throw java.io.IOException("Download failed with HTTP ${response.code}: ${response.message}")
                    }

                    val isAppend = response.code == 206 && existingBytes > 0L
                    val body = response.body ?: throw java.io.IOException("Response body is null")

                    val contentLength = body.contentLength()
                    val totalExpected = if (isAppend && contentLength > 0) existingBytes + contentLength else contentLength
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(partialFile, isAppend)

                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalBytes: Long = if (isAppend) existingBytes else 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (totalExpected > 0) {
                            onProgress((totalBytes.toFloat() / totalExpected).coerceIn(0f, 1f))
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    // Verify that we downloaded a valid non-empty audio file (at least 15KB)
                    if (totalBytes < 15000L || !partialFile.exists() || partialFile.length() < 15000L) {
                        if (partialFile.exists()) partialFile.delete()
                        throw java.io.IOException("Downloaded file too small ($totalBytes bytes)")
                    }

                    // Atomically replace destination file
                    if (destinationFile.exists()) {
                        destinationFile.delete()
                    }
                    val renamed = partialFile.renameTo(destinationFile)
                    if (renamed && destinationFile.exists()) {
                        Log.i("EpisodeDownloader", "Successfully completed resumable download (${destinationFile.length()} bytes) to: ${destinationFile.absolutePath}")
                        onProgress(1.0f)
                        destinationFile.absolutePath
                    } else {
                        if (partialFile.exists()) partialFile.delete()
                        throw java.io.IOException("Failed to rename partial file to destination")
                    }
                } catch (e: Exception) {
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e("EpisodeDownloader", "Exception during download after retry policy: ${e.message}", e)
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
