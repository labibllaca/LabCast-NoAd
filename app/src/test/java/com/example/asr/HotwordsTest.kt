package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class HotwordsTest {

    @Test
    fun `uppercases dedupes and caps at one hundred lines`() {
        val lines = Hotwords.build(listOf("AG1", "ag1", "BetterHelp"), emptyList()).lines()
        assertEquals(listOf("AG1", "BETTERHELP"), lines)
        assertEquals(100, Hotwords.build((1..150).map { "brand$it" }, emptyList()).lines().size)
    }

    @Test
    fun `includes cue phrases and skips blanks`() {
        val out = Hotwords.build(emptyList(), listOf("promo code", " "))
        assertEquals("PROMO CODE", out)
    }
}
