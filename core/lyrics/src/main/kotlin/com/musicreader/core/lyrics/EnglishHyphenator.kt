package com.musicreader.core.lyrics

/**
 * English syllables for singing, built on Frank Liang's hyphenation algorithm (the one TeX
 * uses), written for MusicReader, with the American English patterns `hyph-en-us` by Gerard
 * D.C. Kuiken (resources/hyphenation; see hyph-en-us.LICENSE.txt and docs/DECISIONS.md D-020).
 *
 * TeX's patterns break words by their parts (mak-ing, oth-er, de-spite), which is right for
 * typesetting but not for singing. So the patterns only decide *which* syllables a word has;
 * the place of each break follows how the word is sung (D-022), like the Spanish rules:
 *
 * 1. The word is cut into vowel groups (a e i o u, and y when it is not a consonant: not at the
 *    start of a word and not before a vowel other than a final e). A final e after a consonant
 *    is silent (a-lone) unless the patterns start a syllable right before it (may-be), and
 *    "-le" after a consonant always counts (ta-ble).
 * 2. A gap between two vowel groups gets a break when the patterns (or the exception list) put a
 *    break anywhere inside it. When the patterns give no usable break, every gap gets one
 *    (to-night, a-bout). A pattern break inside a vowel group is a hiatus and is kept (cre-ate).
 * 3. Inside a gap, one consonant goes to the next syllable (ma-king, o-pen); th sh ch ph wh ck
 *    and the blends pl bl cl gl fl pr br tr dr cr gr fr wr stay together and go to the next
 *    syllable (o-ther, ta-ble); gh stays with the syllable before (laugh-ing); other pairs are
 *    split (des-pite, sum-mer); with three or more consonants the last two go on when they stay
 *    together or are st/sp/sk/sc, otherwise only the last one (hun-dred, un-der-stand,
 *    pump-kin); before a final "-le" the
 *    consonant before the l goes on (lit-tle, can-dle).
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
        val raw = exceptionBreaks[word] ?: liangBreaks(word)
        val all = vowelGroups(word, finalEAlwaysSilent = false)
        // A final e is silent (a-lone) unless the patterns start a syllable right before it (may-be).
        val finalE = all.lastOrNull()?.takeIf { isSilentCandidate(word) && it.first == word.length - 1 }
        val keepFinalE = finalE != null && all.size >= 2 && raw.any { it > all[all.size - 2].last && it <= finalE.first }
        val groups = if (finalE != null && !keepFinalE) all.dropLast(1) else all

        val breaks = sortedSetOf<Int>()
        // Hiatus: a pattern break between two vowels of the same group.
        raw.filter { b -> groups.any { b > it.first && b <= it.last } }.forEach { breaks += it }
        // Gaps between vowel groups that contain a pattern break, or all of them when no pattern
        // break is usable.
        val gaps = (0 until groups.size - 1)
        val withBreak = gaps.filter { g -> raw.any { it > groups[g].last && it <= groups[g + 1].first } }
        val chosen = if (withBreak.isEmpty() && breaks.isEmpty()) gaps.toList() else withBreak
        chosen.forEach { g -> breaks += placeBreak(word, groups[g], groups[g + 1]) }
        return breaks.filter { it >= leftMin && word.length - it >= rightMin }
    }

    /** The raw Liang breaks (positions where a new part starts), before any singing rules. */
    internal fun liangBreaks(word: String): List<Int> {
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
        return (1 until word.length).filter { p -> points[p + 1] % 2 == 1 }
    }

    /** Where the next syllable starts in the consonants between two vowel groups. */
    private fun placeBreak(word: String, left: IntRange, right: IntRange): Int {
        val start = left.last + 1
        val end = right.first // exclusive
        val n = end - start
        fun pair(at: Int) = word.substring(at, at + 2)
        val finalLe = right.first == word.length - 1 && word[right.first] == 'e' && word[right.first - 1] == 'l'
        return when {
            n <= 1 -> start
            finalLe -> end - 2 // lit-tle, can-dle, ta-ble
            n == 2 && pair(start) == "gh" -> end // laugh-ing
            n == 2 && staysTogether(pair(start)) -> start // o-ther, ta-ble
            n == 2 -> start + 1 // des-pite, sum-mer
            staysTogether(pair(end - 2)) || pair(end - 2) in S_CLUSTERS -> end - 2 // hun-dred, un-der-stand
            else -> end - 1 // mons-ter, pump-kin
        }
    }

    private fun staysTogether(pair: String): Boolean = pair in DIGRAPHS || pair in BLENDS

    /** A final e after a consonant, except "-le" after a consonant (ta-ble), may be silent. */
    private fun isSilentCandidate(word: String): Boolean {
        val n = word.length
        return n >= 3 && word[n - 1] == 'e' && !isVowelAt(word, n - 2) &&
            !(word[n - 2] == 'l' && !isVowelAt(word, n - 3))
    }

    /** Vowel groups as index ranges. With [finalEAlwaysSilent], a silent final e is left out. */
    internal fun vowelGroups(word: String, finalEAlwaysSilent: Boolean = true): List<IntRange> {
        val n = word.length
        val scanEnd = if (finalEAlwaysSilent && isSilentCandidate(word)) n - 1 else n
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
        return groups
    }

    /** y is a vowel except at the start of a word or before another vowel (you, yes, beyond). */
    private fun isVowelAt(word: String, i: Int): Boolean {
        val c = word[i]
        if (c in VOWELS) return true
        if (c != 'y') return false
        if (i == 0) return false
        val next = word.getOrNull(i + 1) ?: return true
        // Before a vowel y is a consonant (beyond), except before a final silent e (bye).
        return next !in VOWELS || (next == 'e' && i + 1 == word.length - 1)
    }

    companion object {
        private const val VOWELS = "aeiou"
        private val DIGRAPHS = setOf("th", "sh", "ch", "ph", "wh", "ck")
        /** Kept together only after another consonant (un-der-stand, but des-pite). */
        private val S_CLUSTERS = setOf("st", "sp", "sk", "sc")
        private val BLENDS = setOf("pl", "bl", "cl", "gl", "fl", "pr", "br", "tr", "dr", "cr", "gr", "fr", "wr")
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
