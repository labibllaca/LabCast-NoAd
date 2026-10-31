package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelRegistryTest {

    @Test
    fun `pins five files with checksums and exact total size`() {
        assertEquals(5, AsrModelRegistry.files.size)
        assertEquals(44_293_155L, AsrModelRegistry.totalBytes)
        AsrModelRegistry.files.forEach {
            assertEquals(64, it.sha256.length)
            assertTrue(it.url.startsWith("https://"))
        }
    }
}
