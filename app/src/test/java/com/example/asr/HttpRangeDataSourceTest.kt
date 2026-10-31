package com.example.asr

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HttpRangeDataSourceTest {

    private fun tempDir(): File = Files.createTempDirectory("asr-range-test").toFile()

    @Test
    fun `readAt fetches a range and caches it`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(206).setBody("abcdefghij"))
        server.start()
        try {
            val source = HttpRangeDataSource(
                server.url("/a.mp3").toString(),
                tempDir(),
                OkHttpClient(),
            )
            val buf = ByteArray(4)
            assertEquals(4, source.readAt(2, buf, 0, 4))
            assertEquals("cdef", String(buf))
            source.readAt(2, buf, 0, 4)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a 200 response falls back to a full read`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("abcdefghij"))
        server.start()
        try {
            val source = HttpRangeDataSource(
                server.url("/a.mp3").toString(),
                tempDir(),
                OkHttpClient(),
            )
            val buf = ByteArray(4)
            assertEquals(4, source.readAt(2, buf, 0, 4))
            assertEquals("cdef", String(buf))
        } finally {
            server.shutdown()
        }
    }
}
