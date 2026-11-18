package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class BpeVocabAssetTest {

    @Test
    fun `bpe vocabulary is bundled for hotword encoding`() {
        val file = File("src/main/assets/asr/bpe.vocab")

        assertTrue("missing ${file.absolutePath}", file.isFile)
        assertEquals(12_590L, file.length())
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals("f191a4935f668fa8cd8e607bcd378404f948321cd3134a5ea13d324ba921673d", sha)
    }
}
