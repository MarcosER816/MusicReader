package com.musicreader.core.lyrics

import com.musicreader.core.model.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishHyphenatorTest {
    private val splitter = WordSyllabifier.forLanguage(Language.EN)

    /** Sung syllables (D-022): one consonant goes to the next syllable, pairs are split. */
    private val words = listOf(
        // Mark's corrections from the Stage 2 device test
        "you", "you'll", "ma-king", "des-pite", "o-ther",
        // Syllables found by the hyph-en-us patterns, placed by the singing rules
        "hel-lo", "hea-ven", "beau-ti-ful", "re-mem-ber", "yes-ter-day", "to-mor-row", "to-ge-ther",
        "be-lieve", "mor-ning", "dan-cing", "wa-ter", "ri-ver", "win-dow", "sha-dow", "mo-ney", "mu-sic",
        "peo-ple", "lit-tle", "ba-by", "a-gain", "a-no-ther", "no-thing", "some-thing", "sor-ry", "hap-py",
        "sum-mer", "win-ter", "free-dom", "me-mo-ry", "har-mo-ny", "won-der-ful", "drea-ming", "rain-bow",
        "un-der-stand", "but-ter-fly", "com-pu-ter", "gui-tar", "good-bye", "o-cean", "can-dle",
        "bro-ken", "ta-ble", "fa-mi-ly", "his-to-ry", "yel-low", "laugh-ing", "daugh-ter", "hun-dred",
        "chil-dren", "mon-ster", "cre-ate", "go-ing", "love-ly", "be-yond", "your-self", "some-times",
        "thin-king", "lone-ly", "for-get",
        // Words the patterns leave whole: split between vowel groups
        "to-night", "sin-ging", "a-bout", "a-way", "a-lone", "cra-zy", "me-lo-dy", "sin-ger", "o-pen", "star-light",
        // One syllable
        "love", "heart", "fire", "rhythm", "night", "through", "strength", "caught",
        // Capitals, apostrophes and punctuation stay as typed
        "Hel-lo,", "don't", "I'm", "sum-mer's", "(o-pen)", "You",
    )

    @Test
    fun splitsEnglishWords() {
        val wrong = words.mapNotNull { expected ->
            val actual = splitter.split(expected.replace("-", "")).joinToString("-")
            if (actual != expected) "$expected -> $actual" else null
        }
        assertEquals(emptyList(), wrong, "wrong splits")
        assertTrue(words.size >= 40)
    }

    @Test
    fun exceptionListIsUsed() {
        assertEquals("ta-ble", splitter.split("table").joinToString("-"))
        assertEquals("as-so-ciate", splitter.split("associate").joinToString("-")) // exception list
    }

    @Test
    fun everySyllableHasAVowel() {
        for (w in listOf("starlight", "strengths", "rhythms", "twelfths", "schmaltz")) {
            for (s in splitter.split(w)) assertTrue(s.any { it in "aeiouy" }, "$w -> $s")
        }
    }

    @Test
    fun syllablesAlwaysRebuildTheWord() {
        for (w in listOf("Hello!", "rock'n'roll", "self-esteem", "--", "x", "BEAUTIFUL")) {
            assertEquals(w, splitter.split(w).joinToString(""))
        }
        assertEquals("BEAU-TI-FUL", splitter.split("BEAUTIFUL").joinToString("-"))
        assertEquals(listOf("self-", "es-", "teem").size, splitter.split("self-esteem").size)
    }
}
