package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelRegistryTest {

    @Test
    fun `pins the 2023-06-26 model`() {
        assertEquals("zipformer-en-2023-06-26", AsrModelRegistry.modelDirName)
        assertEquals(5, AsrModelRegistry.files.size)
        assertEquals(73_298_636L, AsrModelRegistry.totalBytes)
        assertTrue(AsrModelRegistry.files.any { it.name.contains("chunk-16-left-128.int8.onnx") })
    }

    @Test
    fun `every file carries a checksum and an https url`() {
        AsrModelRegistry.files.forEach {
            assertEquals(64, it.sha256.length)
            assertTrue(it.url.startsWith("https://"))
        }
    }
}
