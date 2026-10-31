package com.example.asr

import android.media.MediaDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.math.min

/**
 * Random-access HTTP source for `MediaExtractor`.
 *
 * Reads are served from 1 MB on-disk chunks so a progressive decoder never
 * refetches the same bytes. Servers that answer with 200 instead of 206 are
 * handled by slicing the full body.
 */
class HttpRangeDataSource(
    private val url: String,
    private val cacheDir: File,
    private val client: OkHttpClient,
) {

    private val lock = Any()
    private val directory: File by lazy {
        val key = (url.hashCode().toLong() and 0xffffffffL).toString(16)
        File(cacheDir, "asr/$key")
    }

    @Volatile
    private var size: Long = -1L

    fun getSize(): Long {
        if (size >= 0L) return size
        synchronized(lock) {
            if (size >= 0L) return size
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                size = response.body?.contentLength() ?: -1L
            }
            if (size < 0L) {
                // Some servers do not answer HEAD correctly; probe with a one-byte range.
                val probe = Request.Builder().url(url).header("Range", "bytes=0-0").build()
                client.newCall(probe).execute().use { response ->
                    val contentRange = response.header("Content-Range")
                    size = contentRange?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                }
            }
            return size
        }
    }

    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0L || size <= 0) return 0
        var written = 0
        var pos = position
        while (written < size) {
            val chunkIndex = pos / CHUNK_SIZE
            val chunk = chunk(chunkIndex)
            if (chunk.isEmpty()) break
            val chunkOffset = (pos % CHUNK_SIZE).toInt()
            if (chunkOffset >= chunk.size) break
            val count = min(size - written, chunk.size - chunkOffset)
            System.arraycopy(chunk, chunkOffset, buffer, offset + written, count)
            written += count
            pos += count
        }
        return written
    }

    fun close() {
        // The on-disk chunk cache is intentionally kept for the next seek.
    }

    private fun chunk(index: Long): ByteArray {
        synchronized(lock) {
            val file = File(directory, "chunk-$index.bin")
            if (file.isFile && file.length() > 0L) return file.readBytes()

            val start = index * CHUNK_SIZE
            val end = start + CHUNK_SIZE - 1
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$start-$end")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                val body = response.body?.bytes() ?: ByteArray(0)
                val data = if (response.code == 206) {
                    body
                } else {
                    val from = start.coerceAtMost(body.size.toLong()).toInt()
                    val to = min(body.size.toLong(), start + body.size).toInt()
                    if (from >= to) ByteArray(0) else body.copyOfRange(from, to)
                }
                if (data.isEmpty()) return ByteArray(0)
                directory.mkdirs()
                file.writeBytes(data)
                return data
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 1L * 1024 * 1024
    }
}

/** Adapts the plain range source to the Android `MediaDataSource` API. */
class HttpMediaDataSource(
    private val delegate: HttpRangeDataSource,
) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        return delegate.readAt(position, buffer, offset, size)
    }

    override fun getSize(): Long = delegate.getSize()

    override fun close() = delegate.close()
}
