package com.musicreader.core.lyrics

import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.Song
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.Word

/**
 * Pure operations on lyrics. Lyrics belong to a [SectionContent], so linked sections share them
 * (D-013). Voice 0 is the main voice and voice 1 a second voice sung at the same time (D-003).
 *
 * Words the user split or merged by hand have `syllableOverride = true`; nothing here ever
 * re-splits them automatically.
 */
object LyricsOps {

    /** The lyrics of [voice] as editable text: one line per lyric line, words separated by spaces. */
    fun voiceText(content: SectionContent, voice: Int): String =
        content.lyricLines.filter { it.voice == voice }.joinToString("\n") { it.text }

    /** Splits text into non-blank lines of words (any whitespace separates words). */
    fun tokenize(text: String): List<List<String>> =
        text.lines()
            .map { line -> line.trim().split(WHITESPACE).filter { it.isNotEmpty() } }
            .filter { it.isNotEmpty() }

    /** A new word split by [splitter]. */
    fun newWord(text: String, splitter: WordSyllabifier, ids: IdGenerator): Word = Word(
        id = ids.newId(),
        text = text,
        syllables = splitter.split(text).map { Syllable(id = ids.newId(), text = it) },
        syllableOverride = false,
    )

    /**
     * Replaces the lyrics of [voice] with [text]. Words that are still there (same text, same
     * order; found with a longest-common-subsequence match) keep their id, syllables, manual
     * splits and timing. Only new or changed words are split by [splitter]. Lines whose text did
     * not change keep their id. Lines of the other voice are untouched.
     */
    fun setVoiceText(
        content: SectionContent,
        voice: Int,
        text: String,
        splitter: WordSyllabifier,
        ids: IdGenerator,
    ): SectionContent {
        val oldLines = content.lyricLines.filter { it.voice == voice }
        val newTokens = tokenize(text)

        val oldWords = oldLines.flatMap { it.words }
        val newWords = newTokens.flatten()
        val reused = matchByLcs(oldWords.map { it.text }, newWords)

        val oldLineIdsByText = oldLines.groupBy({ it.text }, { it.id }).mapValues { it.value.toMutableList() }
        var flatIndex = 0
        val lines = newTokens.map { tokens ->
            val words = tokens.map { token ->
                val oldIndex = reused[flatIndex++]
                if (oldIndex != null) oldWords[oldIndex] else newWord(token, splitter, ids)
            }
            val lineText = tokens.joinToString(" ")
            val id = oldLineIdsByText[lineText]?.removeFirstOrNull() ?: ids.newId()
            LyricLine(id = id, voice = voice, text = lineText, words = words)
        }
        // Lines are stored main voice first; the sort is stable, so each voice keeps its order.
        return content.copy(lyricLines = (content.lyricLines.filter { it.voice != voice } + lines).sortedBy { it.voice })
    }

    /**
     * Splits syllable [syllableIndex] of [word] before character [offset] of that syllable
     * (1 until its length). The word becomes a manual split. The two new syllables have no timing.
     */
    fun splitSyllable(word: Word, syllableIndex: Int, offset: Int, ids: IdGenerator): Word {
        val syllable = word.syllables.getOrNull(syllableIndex) ?: return word
        if (offset <= 0 || offset >= syllable.text.length) return word
        val first = Syllable(id = ids.newId(), text = syllable.text.substring(0, offset))
        val second = Syllable(id = ids.newId(), text = syllable.text.substring(offset))
        val syllables = word.syllables.toMutableList().apply {
            removeAt(syllableIndex)
            addAll(syllableIndex, listOf(first, second))
        }
        return word.copy(syllables = syllables, syllableOverride = true)
    }

    /**
     * Joins syllable [leftIndex] with the next one. The word becomes a manual split. The merged
     * syllable keeps the start and pitch of the first part and spans both durations when both
     * were timed; otherwise it has no timing.
     */
    fun mergeSyllables(word: Word, leftIndex: Int, ids: IdGenerator): Word {
        val left = word.syllables.getOrNull(leftIndex) ?: return word
        val right = word.syllables.getOrNull(leftIndex + 1) ?: return word
        val timed = left.startBeat != null && left.durationBeats != null &&
            right.startBeat != null && right.durationBeats != null
        val merged = if (timed) {
            Syllable(
                id = ids.newId(),
                text = left.text + right.text,
                startBeat = left.startBeat,
                durationBeats = right.startBeat!! + right.durationBeats!! - left.startBeat!!,
                pitchStartMidi = left.pitchStartMidi,
                pitchEndMidi = right.pitchEndMidi,
                freestyle = left.freestyle && right.freestyle,
            )
        } else {
            Syllable(id = ids.newId(), text = left.text + right.text)
        }
        val syllables = word.syllables.toMutableList().apply {
            removeAt(leftIndex + 1)
            set(leftIndex, merged)
        }
        return word.copy(syllables = syllables, syllableOverride = true)
    }

    /** Throws away manual changes to [word] and splits it automatically again. */
    fun resetWord(word: Word, splitter: WordSyllabifier, ids: IdGenerator): Word =
        newWord(word.text, splitter, ids).copy(id = word.id)

    /**
     * Splits again every word that was not split by hand (for example after the lyrics language
     * changed). Words whose automatic split is unchanged keep their syllables and timing.
     */
    fun resplitAutomatic(song: Song, splitter: WordSyllabifier, ids: IdGenerator): Song = song.copy(
        sectionContents = song.sectionContents.map { content ->
            content.copy(
                lyricLines = content.lyricLines.map { line ->
                    line.copy(
                        words = line.words.map { word ->
                            if (word.syllableOverride) {
                                word
                            } else {
                                val parts = splitter.split(word.text)
                                if (parts == word.syllables.map { it.text }) word else resetWord(word, splitter, ids)
                            }
                        },
                    )
                },
            )
        },
    )

    /** Applies [change] to one word of a section content; unknown ids leave it unchanged. */
    fun updateWord(content: SectionContent, wordId: String, change: (Word) -> Word): SectionContent =
        content.copy(
            lyricLines = content.lyricLines.map { line ->
                if (line.words.none { it.id == wordId }) line
                else line.copy(words = line.words.map { if (it.id == wordId) change(it) else it })
            },
        )

    /**
     * For each item of [new], the index of the [old] item it keeps (longest common subsequence of
     * equal texts), or null for new items.
     */
    internal fun matchByLcs(old: List<String>, new: List<String>): List<Int?> {
        val n = old.size
        val m = new.size
        val lengths = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lengths[i][j] = if (old[i] == new[j]) lengths[i + 1][j + 1] + 1
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val result = arrayOfNulls<Int>(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                old[i] == new[j] -> { result[j] = i; i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return result.toList()
    }

    private val WHITESPACE = Regex("\\s+")
}
