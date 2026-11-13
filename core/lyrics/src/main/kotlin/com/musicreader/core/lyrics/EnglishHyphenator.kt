package com.musicreader.core.lyrics

/**
 * Frank Liang's hyphenation algorithm (the one TeX uses), written for MusicReader, with the
 * American English patterns `hyph-en-us` by Gerard D.C. Kuiken (resources/hyphenation; see
 * hyph-en-us.LICENSE.txt and docs/DECISIONS.md D-020).
 *
 * Every substring of ".word." that is a pattern contributes its digits; at each position the
 * highest digit wins and odd values allow a break. [leftMin] and [rightMin] are the shortest
 * first and last syllables allowed. TeX uses 2 and 3 for typesetting; for sung syllables
 * MusicReader uses 1 and 2.
 *
 * Two additions for singing (D-020):
 * - every syllable must contain a vowel (a, e, i, o, u, y), so "s-tarlight" cannot happen;
 * - TeX's patterns never split off short edge syllables ("about", "tonight", "singing"). When
 *   the patterns give no usable break, [vowelGroupBreaks] splits the word between its vowel
 *   groups: "-ing" is its own syllable, one consonant moves to the next syllable (a-bout,
 *   to-night), two are split (sin-ger) unless they are th/sh/ch/ph/wh/gh, and a silent final e
 *   does not count (a-lone).
 */
class EnglishHyphenator(
    patterns: Sequence<String>,
    exceptions: Sequence<String>,
    private val leftMin: Int = 1,
    private val rightMin: Int = 2,
) : Syllabifier {

    private val patternValues: Map<String, IntArray>
    private val maxPatternLength: Int
    private val exceptionBreaks: Map<String, List<Int>>

    init {
        val map = HashMap<String, IntArray>()
        for (raw in patterns) {
            val pattern = raw.trim()
            if (pattern.isEmpty() || pattern.startsWith("%")) continue
            val letters = StringBuilder()
            val values = IntArray(pattern.count { !it.isDigit() } + 1)
            for (c in pattern) {
                if (c.isDigit()) values[letters.length] = c - '0' else letters.append(c)
            }
            map[letters.toString()] = values
        }
        patternValues = map
        maxPatternLength = map.keys.maxOf { it.length }

        exceptionBreaks = exceptions.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("%") }
            .associate { entry ->
                val breaks = mutableListOf<Int>()
                var letters = 0
                for (c in entry) if (c == '-') breaks += letters else letters++
                entry.replace("-", "") to breaks
            }
    }

    override fun breakPoints(letters: String): List<Int> {
        val word = letters.lowercase()
        exceptionBreaks[word]?.let { breaks -> return breaks.filter { allowed(word.length, it) } }
        val dotted = ".$word."
        val points = IntArray(dotted.length + 1)
        for (start in dotted.indices) {
            val end = minOf(dotted.length, start + maxPatternLength)
            for (stop in start + 1..end) {
                val values = patternValues[dotted.substring(start, stop)] ?: continue
                for (k in values.indices) {
                    if (values[k] > points[start + k]) points[start + k] = values[k]
                }
            }
        }
        // points[p + 1] is the value between word[p - 1] and word[p] (shifted by the leading dot).
        val fromPatterns = (1 until word.length).filter { p -> points[p + 1] % 2 == 1 && allowed(word.length, p) }
        return withVowelInEverySyllable(word, fromPatterns)
            .ifEmpty { withVowelInEverySyllable(word, vowelGroupBreaks(word)) }
    }

    /** Drops breaks that would leave a syllable without a vowel. */
    private fun withVowelInEverySyllable(word: String, breaks: List<Int>): List<Int> {
        val kept = mutableListOf<Int>()
        var start = 0
        for (b in breaks) {
            if (hasVowel(word, start, b) && hasVowel(word, b, word.length)) {
                kept += b
                start = b
            }
        }
        return kept
    }

    private fun hasVowel(word: String, from: Int, to: Int): Boolean = (from until to).any { word[it] in VOWELS_Y }

    /** Fallback split between vowel groups, used only when the patterns give no break. */
    internal fun vowelGroupBreaks(word: String): List<Int> {
        val breaks = mutableListOf<Int>()
        var end = word.length
        if (end > 4 && word.endsWith("ing") && word[end - 4] !in VOWELS) {
            breaks += end - 3
            end -= 3
        }
        // A final e after a consonant is silent (alone, fire), except in "-le" (table).
        val silentE = end == word.length && end >= 3 && word[end - 1] == 'e' && word[end - 2] !in VOWELS &&
            !(word[end - 2] == 'l' && word[end - 3] !in VOWELS)
        val scanEnd = if (silentE) end - 1 else end

        val groups = mutableListOf<IntRange>()
        var i = 0
        while (i < scanEnd) {
            if (isVowelAt(word, i)) {
                val start = i
                while (i + 1 < scanEnd && isVowelAt(word, i + 1)) i++
                groups += start..i
            }
            i++
        }
        for (g in 0 until groups.size - 1) {
            val consonantStart = groups[g].last + 1
            val consonantCount = groups[g + 1].first - consonantStart
            when {
                consonantCount == 1 -> breaks += consonantStart
                consonantCount == 2 && word.substring(consonantStart, consonantStart + 2) in DIGRAPHS ->
                    breaks += consonantStart
                consonantCount >= 2 -> breaks += consonantStart + 1
            }
        }
        return breaks.filter { allowed(word.length, it) }.sorted()
    }

    /** y is a vowel except at the start of a word or before another vowel (yes, beyond). */
    private fun isVowelAt(word: String, i: Int): Boolean {
        val c = word[i]
        if (c in VOWELS) return true
        if (c != 'y') return false
        return i > 0 && word.getOrNull(i + 1)?.let { it !in VOWELS } ?: true
    }

    private fun allowed(length: Int, position: Int): Boolean =
        position >= leftMin && length - position >= rightMin

    companion object {
        private const val VOWELS = "aeiou"
        private const val VOWELS_Y = "aeiouy"
        private val DIGRAPHS = setOf("th", "sh", "ch", "ph", "wh", "gh")
        private const val PATTERNS = "/hyphenation/hyph-en-us.pat.txt"
        private const val EXCEPTIONS = "/hyphenation/hyph-en-us.hyp.txt"

        /** Loaded once from the bundled pattern files. */
        val default: EnglishHyphenator by lazy {
            fun lines(path: String): Sequence<String> {
                val stream = EnglishHyphenator::class.java.getResourceAsStream(path)
                    ?: error("Missing resource $path")
                return stream.bufferedReader(Charsets.UTF_8).use { it.readLines() }.asSequence()
            }
            EnglishHyphenator(lines(PATTERNS).flatMap { it.split(' ') }, lines(EXCEPTIONS))
        }
    }
}
