package com.musicreader.core.lyrics

import com.musicreader.core.model.Language

/** Finds syllable boundaries in a word that contains letters only. */
fun interface Syllabifier {
    /**
     * Indices in [letters] where a new syllable starts (never 0, ascending). [letters] contains
     * only letters, in any case.
     */
    fun breakPoints(letters: String): List<Int>

    companion object {
        fun forLanguage(language: Language): Syllabifier = when (language) {
            Language.ES -> SpanishSyllabifier
            Language.EN -> EnglishHyphenator.default
        }
    }
}

/**
 * Splits a word as typed (with capitals, accents and punctuation such as `"¿Dónde,"`) into
 * syllable texts whose concatenation is exactly the input.
 *
 * Only the letters are given to the [Syllabifier]; every other character stays attached to the
 * letter before it (leading punctuation to the first syllable). A hyphen inside a word always
 * ends a syllable ("bien-estar" keeps "bien-" apart).
 */
class WordSyllabifier(private val syllabifier: Syllabifier) {

    fun split(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        // Letter runs separated by hyphens are syllabified on their own.
        val cuts = sortedSetOf<Int>()
        var runStart = -1
        val letters = StringBuilder()
        val letterIndex = mutableListOf<Int>()

        fun flushRun() {
            if (letters.isNotEmpty()) {
                syllabifier.breakPoints(letters.toString()).forEach { cuts += letterIndex[it] }
                if (runStart > 0) cuts += letterIndex[0]
            }
            letters.clear()
            letterIndex.clear()
        }

        word.forEachIndexed { i, c ->
            when {
                c.isLetter() -> {
                    if (letters.isEmpty()) runStart = i
                    letters.append(c)
                    letterIndex += i
                }
                c == '-' -> flushRun()
                else -> Unit // apostrophes, punctuation: ignored by the syllabifier, kept in the text
            }
        }
        flushRun()

        // A cut must leave letters on both sides; otherwise punctuation would become a syllable.
        val firstLetter = word.indexOfFirst { it.isLetter() }
        val lastLetter = word.indexOfLast { it.isLetter() }
        val valid = cuts.filter { it > firstLetter && it <= lastLetter && firstLetter >= 0 }
        val parts = mutableListOf<String>()
        var start = 0
        for (cut in valid) {
            parts += word.substring(start, cut)
            start = cut
        }
        parts += word.substring(start)
        return parts
    }

    companion object {
        fun forLanguage(language: Language): WordSyllabifier = WordSyllabifier(Syllabifier.forLanguage(language))
    }
}
