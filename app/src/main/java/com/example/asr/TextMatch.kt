package com.example.asr

import java.util.Locale
import kotlin.math.max

/**
 * Text normalization and forgiving phrase matching for recognized speech.
 *
 * ASR output for ad reads is full of brand names, phone numbers, and promo
 * codes, and the small streaming model mishears them ("brought to you buy",
 * "square space"). Matching works on normalized words and tolerates one bad
 * word per cue without becoming loose enough to fire on ordinary talk.
 */
object TextMatch {

    private val DIGIT_WORDS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    )

    private val SMALL_WORDS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )

    private val TENS_WORDS = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    private val CANONICAL = mapOf(
        "buy" to "by",
        "bye" to "by",
        "kode" to "code",
        "sight" to "site",
    )

    /** Lowercase words, digits spelled out, punctuation gone, single spaces. */
    fun normalize(text: String): String {
        var working = text.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("+", " plus ")
            .replace("%", " percent ")
        working = working.replace(Regex("(?<=\\d)-(?=\\d)"), "")
        working = working.replace(Regex("[^a-z0-9\\s]"), " ")
        working = working.replace(Regex("(?<=[a-z])(?=\\d)"), " ")
        working = working.replace(Regex("(?<=\\d)(?=[a-z])"), " ")

        return working.split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { token ->
                if (token.all { it.isDigit() }) spellDigits(token) else token
            }
    }

    /** Normalized tokens with the small canonical map applied. */
    fun tokens(text: String): List<String> =
        normalize(text).split(' ')
            .filter { it.isNotEmpty() }
            .map { CANONICAL[it] ?: it }

    /** True when [needle] appears in [haystack] up to [threshold] word-edit similarity. */
    fun fuzzyContains(haystack: String, needle: String, threshold: Double = 0.84): Boolean {
        val hay = tokens(haystack)
        val target = tokens(needle)
        if (hay.isEmpty() || target.isEmpty()) return false
        if (hay.size == target.size && hay == target) return true

        val minWindow = max(1, target.size - 1)
        val maxWindow = target.size + 1
        for (size in minWindow..maxWindow) {
            if (size > hay.size) break
            for (start in 0..(hay.size - size)) {
                val candidate = hay.subList(start, start + size).joinToString(" ")
                if (similarity(candidate, target.joinToString(" ")) >= threshold) return true
            }
        }
        return false
    }

    /** Compact phonetic key so homophones and split words meet. */
    fun phoneticKey(word: String): String {
        var working = word.uppercase(Locale.ROOT).filter { it in 'A'..'Z' }
        if (working.isEmpty()) return ""
        if (working.startsWith("WR")) working = working.removePrefix("W")
        working = working
            .replace("PH", "F")
            .replace("DG", "J")
            .replace("TH", "T")
            .replace("GH", "")
            .replace("X", "KS")

        val out = StringBuilder()
        working.forEachIndexed { index, char ->
            val mapped = when (char) {
                'B', 'P' -> 'P'
                'F', 'V' -> 'F'
                'C', 'Q', 'G' -> 'K'
                'Z' -> 'S'
                else -> char
            }
            val isVowel = mapped in "AEIOU"
            if (isVowel && index > 0) return@forEachIndexed
            if (out.isNotEmpty() && out.last() == mapped) return@forEachIndexed
            out.append(mapped)
        }
        return out.toString()
    }

    /** True when [brand] appears exactly, phonetically, or fuzzily in [haystack]. */
    fun brandMatches(haystack: String, brand: String): Boolean {
        val hay = tokens(haystack)
        val target = tokens(brand)
        if (hay.isEmpty() || target.isEmpty()) return false

        for (size in max(1, target.size - 1)..(target.size + 1)) {
            if (size > hay.size) break
            for (start in 0..(hay.size - size)) {
                val window = hay.subList(start, start + size)
                if (window == target) return true
                if (window.size == target.size &&
                    window.indices.all { phoneticKey(window[it]) == phoneticKey(target[it]) }
                ) {
                    return true
                }
            }
        }
        return fuzzyContains(haystack, brand, 0.70)
    }

    private fun spellDigits(run: String): String {
        if (run.length >= 4 || run.startsWith("0")) {
            return run.map { DIGIT_WORDS[it - '0'] }.joinToString(" ")
        }
        return cardinal(run.toInt())
    }

    private fun cardinal(value: Int): String {
        if (value < 20) return SMALL_WORDS[value]
        if (value < 100) {
            val tens = TENS_WORDS[value / 10]
            val rest = value % 10
            return if (rest == 0) tens else "$tens ${SMALL_WORDS[rest]}"
        }
        val hundreds = "${SMALL_WORDS[value / 100]} hundred"
        val rest = value % 100
        return if (rest == 0) hundreds else "$hundreds ${cardinal(rest)}"
    }

    private fun similarity(a: String, b: String): Double {
        val longest = max(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest.toDouble()
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
