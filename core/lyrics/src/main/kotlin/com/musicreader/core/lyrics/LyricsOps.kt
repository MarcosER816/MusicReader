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

    /** The main voice as editable text: one line per lyric line, words separated by spaces. */
    fun mainText(content: SectionContent): String =
        normalized(content).lyricLines.filter { it.voice == LyricLine.MAIN_VOICE }.joinToString("\n") { it.text }

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
     * Replaces the main voice with [text].
     *
     * - Words that are still there (same text, same order; longest-common-subsequence match)
     *   keep their id, syllables, manual splits and timing. Only new or changed words are split.
     * - Lines keep their id when their text is unchanged, and also when they were edited in
     *   place (matched by position between unchanged lines), so their second-voice line stays
     *   attached. Second-voice lines of removed main lines are removed.
     */
    fun setMainText(content: SectionContent, text: String, splitter: WordSyllabifier, ids: IdGenerator): SectionContent {
        val current = normalized(content)
        val oldLines = current.lyricLines.filter { it.voice == LyricLine.MAIN_VOICE }
        val newTokens = tokenize(text)
        val newTexts = newTokens.map { it.joinToString(" ") }

        val oldWords = oldLines.flatMap { it.words }
        val reusedWords = matchByLcs(oldWords.map { it.text }, newTokens.flatten())
        val reusedLines = matchLines(oldLines.map { it.text }, newTexts)

        var flatIndex = 0
        val mainLines = newTokens.mapIndexed { i, tokens ->
            val words = tokens.map { token ->
                val oldIndex = reusedWords[flatIndex++]
                if (oldIndex != null) oldWords[oldIndex] else newWord(token, splitter, ids)
            }
            val id = reusedLines[i]?.let { oldLines[it].id } ?: ids.newId()
            LyricLine(id = id, voice = LyricLine.MAIN_VOICE, text = newTexts[i], words = words)
        }
        val mainIds = mainLines.mapTo(HashSet()) { it.id }
        val second = current.lyricLines.filter { it.voice == LyricLine.SECOND_VOICE && it.pairedLineId in mainIds }
        return ordered(current.copy(lyricLines = mainLines + second))
    }

    /**
     * Sets the second-voice line sung with main line [mainLineId] (D-022). Blank text removes it.
     * Unchanged words keep their syllables and timing.
     */
    fun setSecondLine(
        content: SectionContent,
        mainLineId: String,
        text: String,
        splitter: WordSyllabifier,
        ids: IdGenerator,
    ): SectionContent {
        val current = normalized(content)
        if (current.lyricLines.none { it.voice == LyricLine.MAIN_VOICE && it.id == mainLineId }) return content
        val existing = secondLineOf(current, mainLineId)
        val others = current.lyricLines.filter { it !== existing }
        val tokens = tokenize(text).flatten()
        if (tokens.isEmpty()) return ordered(current.copy(lyricLines = others))
        val oldWords = existing?.words.orEmpty()
        val reused = matchByLcs(oldWords.map { it.text }, tokens)
        val words = tokens.mapIndexed { i, token -> reused[i]?.let { oldWords[it] } ?: newWord(token, splitter, ids) }
        val line = LyricLine(
            id = existing?.id ?: ids.newId(),
            voice = LyricLine.SECOND_VOICE,
            text = tokens.joinToString(" "),
            words = words,
            pairedLineId = mainLineId,
        )
        return ordered(current.copy(lyricLines = others + line))
    }

    /** The second-voice line of main line [mainLineId], if any. */
    fun secondLineOf(content: SectionContent, mainLineId: String): LyricLine? =
        content.lyricLines.firstOrNull { it.voice == LyricLine.SECOND_VOICE && it.pairedLineId == mainLineId }

    /**
     * Brings older or inconsistent data in line with D-022: a second-voice line without a main
     * line gets the next main line that has none (in order), and second-voice lines that still
     * have no main line (more second lines than main lines, or their main line is gone) are
     * dropped. Lines are ordered main voice first, each second line after the main lines.
     */
    fun normalized(content: SectionContent): SectionContent {
        val main = content.lyricLines.filter { it.voice == LyricLine.MAIN_VOICE }
        val mainIds = main.map { it.id }.toSet()
        val second = content.lyricLines.filter { it.voice == LyricLine.SECOND_VOICE }
        val paired = second.filter { it.pairedLineId in mainIds }.distinctBy { it.pairedLineId }
        val taken = paired.mapTo(HashSet()) { it.pairedLineId }
        val free = main.map { it.id }.filter { it !in taken }.iterator()
        val adopted = second.filter { it.pairedLineId == null }.mapNotNull { line ->
            if (free.hasNext()) line.copy(pairedLineId = free.next()) else null
        }
        val result = ordered(content.copy(lyricLines = main + paired + adopted))
        return if (result == content) content else result
    }

    /** Main lines in their order, then the second-voice lines in the order of their main lines. */
    private fun ordered(content: SectionContent): SectionContent {
        val main = content.lyricLines.filter { it.voice == LyricLine.MAIN_VOICE }
        val position = main.withIndex().associate { (i, line) -> line.id to i }
        val second = content.lyricLines.filter { it.voice == LyricLine.SECOND_VOICE }
            .sortedBy { position[it.pairedLineId] ?: Int.MAX_VALUE }
        return content.copy(lyricLines = main + second)
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

    /**
     * For each new line, the old line whose id it keeps: equal texts first (longest common
     * subsequence), then, between two equal lines, the remaining old and new lines in order
     * (a line edited in place keeps its id).
     */
    internal fun matchLines(old: List<String>, new: List<String>): List<Int?> {
        val result = matchByLcs(old, new).toMutableList()
        var lastOld = -1
        var j = 0
        while (j <= new.size) {
            // Find the next matched new line (or the end) and the old index it matched.
            var k = j
            while (k < new.size && result[k] == null) k++
            val nextOld = if (k < new.size) result[k]!! else old.size
            var o = lastOld + 1
            for (n in j until k) {
                if (o < nextOld) result[n] = o++
            }
            if (k < new.size) lastOld = result[k]!!
            j = k + 1
        }
        return result
    }

    private val WHITESPACE = Regex("\\s+")
}
