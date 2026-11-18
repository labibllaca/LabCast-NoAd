package com.example.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test
    fun `normalizes digits percent ampersand and glued numbers`() {
        assertEquals(
            "use code save fifty for fifty percent off at one eight zero zero five five five zero one zero zero",
            TextMatch.normalize("Use code SAVE50 for 50% off at 1-800-555-0100!"),
        )
    }

    @Test
    fun `tokens fold buy to by`() {
        assertEquals(listOf("brought", "to", "you", "by"), TextMatch.tokens("Brought to you buy"))
    }

    @Test
    fun `fuzzy contains tolerates one wrong word`() {
        assertTrue(
            TextMatch.fuzzyContains(
                "this episode is brought to you buy better help",
                "brought to you by",
            )
        )
    }

    @Test
    fun `fuzzy contains rejects an unrelated phrase`() {
        assertFalse(TextMatch.fuzzyContains("and now back to our chat", "back to the show"))
    }

    @Test
    fun `phonetic key joins homophones`() {
        assertEquals(TextMatch.phoneticKey("phone"), TextMatch.phoneticKey("fone"))
        assertEquals(TextMatch.phoneticKey("right"), TextMatch.phoneticKey("write"))
        assertNotEquals(TextMatch.phoneticKey("help"), TextMatch.phoneticKey("health"))
    }

    @Test
    fun `brand matches across spacing and letter digits`() {
        assertTrue(TextMatch.brandMatches("square space dot com slash show", "Squarespace"))
        assertTrue(TextMatch.brandMatches("i drink ag one every morning", "AG1"))
        assertFalse(TextMatch.brandMatches("i drink water every morning", "AG1"))
    }
}
