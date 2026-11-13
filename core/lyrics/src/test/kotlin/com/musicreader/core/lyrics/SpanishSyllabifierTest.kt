package com.musicreader.core.lyrics

import com.musicreader.core.model.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class SpanishSyllabifierTest {
    private val splitter = WordSyllabifier.forLanguage(Language.ES)

    /** Expected splits, syllables separated by "-". */
    private val words = listOf(
        // Required examples
        "pa-ís", "ciu-dad", "a-ho-ra", "trans-por-te", "ca-rro", "le-er",
        // Open and closed syllables
        "ca-sa", "me-sa", "so-l", "can-ción", "cam-po", "ár-bol", "lá-piz", "co-ra-zón", "mú-si-ca", "gui-ta-rra",
        // Digraphs ch, ll, rr
        "mu-cha-cho", "ca-lle", "lla-ve", "pe-rro", "co-che", "ba-rrio", "ga-lle-ta", "a-rroz",
        // Inseparable groups
        "o-tro", "ha-blar", "a-brir", "si-glo", "flo-res", "pa-dre", "ca-bra", "re-fle-jo", "te-cla", "a-pre-tar",
        // Separable pairs and three or four consonants
        "lis-ta", "al-to", "can-tar", "ins-truc-ción", "cons-truc-ción", "obs-tá-cu-lo", "pers-pec-ti-va", "abs-trac-to", "trans-pa-ren-te", "com-pras",
        // Diphthongs and triphthongs
        "bue-no", "cie-lo", "nue-ve", "rei-na", "au-to", "cuí-da-te", "a-ve-ri-guáis", "U-ru-guay", "buey", "vein-te",
        // Hiatus (two strong vowels, accented i/u)
        "rí-o", "dí-a", "o-ír", "ba-úl", "re-ír", "a-é-re-o", "ma-íz", "le-ón", "po-e-ma", "te-a-tro",
        // h, qu, gu, gü, y, x
        "hue-vo", "a-hí", "bú-ho", "que-so", "qui-zás", "gue-rra", "gui-so", "pin-güi-no", "ver-güen-za", "ma-yo", "re-yes", "hoy", "muy", "y", "e-xa-men", "ta-xi",
        // Capitals and punctuation stay as typed
        "¿Dón-de", "Ma-rí-a,", "can-tar!", "«to-do»",
    ).map { it.replace("so-l", "sol") }

    @Test
    fun splitsSpanishWords() {
        val wrong = words.mapNotNull { expected ->
            val actual = splitter.split(expected.replace("-", "")).joinToString("-")
            if (actual != expected) "$expected -> $actual" else null
        }
        assertEquals(emptyList(), wrong, "wrong splits")
        assert(words.size >= 60)
    }

    @Test
    fun syllablesAlwaysRebuildTheWord() {
        for (w in listOf("¿Qué?", "...", "123", "a", "Ahí-va", "corazón,", "x")) {
            assertEquals(w, splitter.split(w).joinToString(""))
        }
        assertEquals(listOf("..."), splitter.split("..."))
    }
}
