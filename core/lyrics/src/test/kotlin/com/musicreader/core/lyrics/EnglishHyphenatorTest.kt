package com.musicreader.core.lyrics

import com.musicreader.core.model.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnglishHyphenatorTest {
    private val splitter = WordSyllabifier.forLanguage(Language.EN)

    private val words = listOf(
        // From the hyph-en-us patterns
        "hel-lo", "heav-en", "for-ev-er", "beau-ti-ful", "re-mem-ber", "yes-ter-day", "to-mor-row", "to-geth-er",
        "be-lieve", "morn-ing", "danc-ing", "wa-ter", "riv-er", "win-dow", "shad-ow", "mon-ey", "mu-sic",
        "peo-ple", "lit-tle", "ba-by", "a-gain", "an-oth-er", "noth-ing", "some-thing", "sor-ry", "hap-py",
        "sum-mer", "win-ter", "free-dom", "mem-o-ry", "har-mo-ny", "won-der-ful", "dream-ing", "rain-bow",
        "with-out", "un-der-stand", "but-ter-fly", "com-put-er", "gui-tar", "good-bye", "o-cean", "can-dle",
        "bro-ken", "ta-ble", "fam-i-ly", "his-to-ry", "yel-low",
        // From the vowel-group fallback (the patterns give no break)
        "to-night", "sing-ing", "a-bout", "a-way", "a-lone", "cra-zy", "me-lo-dy", "sin-ger", "o-pen", "star-light",
        // One syllable
        "love", "heart", "fire", "rhythm", "night", "through", "strength",
        // Capitals, apostrophes and punctuation stay as typed
        "Hel-lo,", "don't", "I'm", "sum-mer's", "(o-pen)",
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
        assertEquals("as-so-ciate", splitter.split("associate").joinToString("-"))
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
