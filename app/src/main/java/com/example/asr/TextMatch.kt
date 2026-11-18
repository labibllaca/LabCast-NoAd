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

    private const val SINGLE_WORD_BRAND_THRESHOLD = 0.90
    private const val MULTI_WORD_BRAND_THRESHOLD = 0.84
    private const val PHONETIC_MIN_LENGTH = 6
    private const val PHONETIC_MIN_SCORE = 0.70
    private const val MIN_TOKEN_SIMILARITY = 0.80

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

    /**
     * True when [needle] appears in [haystack] as a same-length token window
     * whose average per-word similarity reaches [threshold].
     *
     * Words are compared one by one, not as one long string: character
     * similarity over a whole phrase lets short cues hide inside ordinary
     * sentences ("a break" in "take a break").
     */
    fun fuzzyContains(haystack: String, needle: String, threshold: Double = 0.84): Boolean {
        val hay = tokens(haystack)
        val target = tokens(needle)
        if (hay.isEmpty() || target.isEmpty() || target.size > hay.size) return false

        for (start in 0..(hay.size - target.size)) {
            val window = hay.subList(start, start + target.size)
            if (window == target) return true
            if (windowSimilarity(window, target) >= threshold) return true
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

    /**
     * True when [brand] appears exactly, as a joined word ("square space" for
     * "Squarespace"), or fuzzily at a brand-safe threshold.
     *
     * Single-word brands need 0.90 so short words do not match by accident.
     * The phonetic rescue is limited to tokens of at least six letters whose
     * fuzzy score sits between 0.70 and 0.84, as the design requires.
     */
    fun brandMatches(haystack: String, brand: String): Boolean {
        val hay = tokens(haystack)
        val target = tokens(brand)
        if (hay.isEmpty() || target.isEmpty()) return false

        if (target.size == 1) {
            val word = target.single()
            if (hay.any { it == word }) return true
            for (size in 2..3) {
                if (size > hay.size) break
                for (start in 0..(hay.size - size)) {
                    if (hay.subList(start, start + size).joinToString("") == word) return true
                }
            }
            return hay.any { candidate ->
                val score = similarity(candidate, word)
                score >= SINGLE_WORD_BRAND_THRESHOLD ||
                    (word.length >= PHONETIC_MIN_LENGTH &&
                        score >= PHONETIC_MIN_SCORE && score < 0.84 &&
                        phoneticKey(candidate) == phoneticKey(word))
            }
        }

        val joinedTarget = target.joinToString("")
        for (start in 0..(hay.size - target.size)) {
            if (hay.subList(start, start + target.size) == target) return true
            if (hay.subList(start, start + target.size).joinToString("") == joinedTarget) return true
        }
        for (size in 1..4) {
            if (size > hay.size) break
            for (start in 0..(hay.size - size)) {
                val joined = hay.subList(start, start + size).joinToString("")
                if (joined == joinedTarget) return true
                if (similarity(joined, joinedTarget) >= MULTI_WORD_BRAND_THRESHOLD) return true
            }
        }
        return false
    }

    private fun windowSimilarity(window: List<String>, target: List<String>): Double {
        if (window.size != target.size || window.isEmpty()) return 0.0
        val scores = window.indices.map { similarity(window[it], target[it]) }
        // One badly heard word inside a cue would otherwise be diluted by the
        // good words; each word must at least resemble its counterpart.
        if (scores.any { it < MIN_TOKEN_SIMILARITY }) return 0.0
        return scores.average()
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
