package com.example.asr

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class ModelDownloadManagerTest {

    private val file = AsrModelFile(
        name = "a.bin",
        url = "http://localhost/a.bin",
        sha256 = MessageDigest.getInstance("SHA-256")
            .digest("abc".toByteArray())
            .joinToString("") { "%02x".format(it) },
        sizeBytes = 3L,
    )

    private fun tempDir(): File = Files.createTempDirectory("asr-model-test").toFile()

    @Test
    fun `download verifies hash and reports progress`() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("abc"))
            start()
        }
        try {
            val file = file.copy(url = server.url("/a.bin").toString())
            val progress = mutableListOf<Float>()
            val manager = ModelDownloadManager(tempDir(), listOf(file))

            assertTrue(manager.download { progress += it }.isSuccess)
            assertEquals(1f, progress.last())
            assertEquals("abc", File(manager.modelDir(), "a.bin").readText())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `download rejects a hash mismatch and deletes the partial file`() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("abd"))
            start()
        }
        try {
            val manager = ModelDownloadManager(
                tempDir(),
                listOf(file.copy(url = server.url("/a.bin").toString())),
            )

            assertTrue(manager.download {}.isFailure)
            assertFalse(File(manager.modelDir(), "a.bin").exists())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `deleteLegacy removes old model dirs and keeps the current one`() {
        val root = tempDir()
        File(root, "zipformer-en-20m-2023-02-17").mkdirs()
        File(root, AsrModelRegistry.modelDirName).mkdirs()
        val manager = ModelDownloadManager(root, emptyList())

        manager.deleteLegacy()

        assertFalse(File(root, "zipformer-en-20m-2023-02-17").exists())
        assertTrue(File(root, AsrModelRegistry.modelDirName).exists())
    }

    @Test
    fun `a server error leaves no partial files and isInstalled stays false`() = runTest {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(500))
            start()
        }
        try {
            val manager = ModelDownloadManager(
                tempDir(),
                listOf(file.copy(url = server.url("/a.bin").toString())),
            )

            assertTrue(manager.download {}.isFailure)
            assertFalse(manager.isInstalled())
            assertEquals(emptyList<String>(), manager.modelDir().listFiles().orEmpty().map { it.name })
        } finally {
            server.shutdown()
        }
    }
}
