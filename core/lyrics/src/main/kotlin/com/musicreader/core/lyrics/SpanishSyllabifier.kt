package com.musicreader.core.lyrics

/**
 * Rule-based Spanish syllabification (written for MusicReader from the standard RAE rules).
 *
 * 1. Letters are grouped into units: the digraphs ch, ll, rr, "qu", and "gu" before e/i count as
 *    one consonant; h is a (silent) consonant; y is a consonant before a vowel and a vowel
 *    otherwise ("rey", "muy", "y").
 * 2. Vowels: a, e, o and the accented á, é, í, ó, ú are strong; i, u, ü and vowel-y are weak.
 *    Two strong vowels are split (hiatus: le-er, pa-ís, rí-o); a weak vowel next to any vowel
 *    forms a diphthong (ciu-dad, hue-vo) or a triphthong (U-ru-guay). Two identical weak vowels
 *    are split (chi-i-ta).
 * 3. Consonants between vowels: one goes to the next syllable (ca-sa, ca-rro); two are split
 *    unless they form an inseparable group pl bl cl gl fl kl pr br tr dr cr gr fr kr (o-tro,
 *    lis-ta); with three or more, the last two stay together when inseparable (ins-truc-ción,
 *    com-pra), otherwise only the last one moves on (trans-por-te, obs-tá-cu-lo).
 */
object SpanishSyllabifier : Syllabifier {

    private enum class Kind { CONSONANT, STRONG_VOWEL, WEAK_VOWEL }

    /** A letter or digraph: [start] is its index in the word, [text] its lowercase letters. */
    private class Unit(val start: Int, val text: String, val kind: Kind) {
        val isVowel: Boolean get() = kind != Kind.CONSONANT
    }

    private const val STRONG = "aeoáéíóú"
    private const val WEAK = "iuü"
    private val INSEPARABLE_L = setOf("p", "b", "c", "g", "f", "k")
    private val INSEPARABLE_R = setOf("p", "b", "t", "d", "c", "g", "f", "k")

    override fun breakPoints(letters: String): List<Int> {
        val units = units(letters.lowercase())
        if (units.none { it.isVowel }) return emptyList()
        val breaks = mutableListOf<Int>()
        var i = 0
        // Skip the consonants before the first vowel: they belong to the first syllable.
        while (!units[i].isVowel) i++
        while (i < units.size) {
            // A run of vowel units, possibly split by hiatus.
            var j = i
            while (j + 1 < units.size && units[j + 1].isVowel) {
                if (isHiatus(units[j], units[j + 1])) breaks += units[j + 1].start
                j++
            }
            // The consonants up to the next vowel (if any).
            var k = j + 1
            while (k < units.size && !units[k].isVowel) k++
            if (k >= units.size) break // trailing consonants stay in the last syllable
            val consonants = units.subList(j + 1, k)
            breaks += consonants.breakBefore()
            i = k
        }
        return breaks
    }

    /** Where the next syllable starts within the consonants between two vowels. */
    private fun List<Unit>.breakBefore(): Int {
        val n = size
        return when {
            n == 0 -> error("no consonants")
            n == 1 -> this[0].start
            n == 2 -> if (inseparable(this[0], this[1])) this[0].start else this[1].start
            else -> if (inseparable(this[n - 2], this[n - 1])) this[n - 2].start else this[n - 1].start
        }
    }

    private fun inseparable(first: Unit, second: Unit): Boolean = when (second.text) {
        "l" -> first.text in INSEPARABLE_L
        "r" -> first.text in INSEPARABLE_R
        else -> false
    }

    private fun isHiatus(a: Unit, b: Unit): Boolean =
        (a.kind == Kind.STRONG_VOWEL && b.kind == Kind.STRONG_VOWEL) ||
            (a.kind == Kind.WEAK_VOWEL && b.kind == Kind.WEAK_VOWEL && a.text == b.text)

    private fun units(s: String): List<Unit> {
        val out = mutableListOf<Unit>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)
            val afterNext = s.getOrNull(i + 2)
            val unit = when {
                c == 'c' && next == 'h' -> Unit(i, "ch", Kind.CONSONANT)
                c == 'l' && next == 'l' -> Unit(i, "ll", Kind.CONSONANT)
                c == 'r' && next == 'r' -> Unit(i, "rr", Kind.CONSONANT)
                c == 'q' && next == 'u' -> Unit(i, "qu", Kind.CONSONANT)
                c == 'g' && next == 'u' && afterNext != null && afterNext in "eiéí" -> Unit(i, "gu", Kind.CONSONANT)
                c == 'y' -> if (next != null && isVowelLetter(next)) {
                    Unit(i, "y", Kind.CONSONANT)
                } else {
                    Unit(i, "y", Kind.WEAK_VOWEL)
                }
                c in STRONG -> Unit(i, c.toString(), Kind.STRONG_VOWEL)
                c in WEAK -> Unit(i, c.toString(), Kind.WEAK_VOWEL)
                else -> Unit(i, c.toString(), Kind.CONSONANT)
            }
            out += unit
            i += unit.text.length
        }
        return out
    }

    private fun isVowelLetter(c: Char): Boolean = c in STRONG || c in WEAK
}
