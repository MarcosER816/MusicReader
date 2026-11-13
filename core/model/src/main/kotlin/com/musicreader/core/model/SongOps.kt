package com.musicreader.core.model

/** The content of [section]. Throws if the song is inconsistent (no such content). */
fun Song.contentOf(section: Section): SectionContent =
    sectionContents.firstOrNull { it.id == section.contentId }
        ?: error("Section ${section.id} points at missing content ${section.contentId}")

/** Number of timeline sections that share [section]'s content (1 = not linked). */
fun Song.linkedCount(section: Section): Int = sections.count { it.contentId == section.contentId }

/**
 * 1-based number of each section among the sections of the same type, for names like
 * "Verse 2". Types that appear only once get no number (null).
 */
fun Song.occurrenceNumbers(): Map<String, Int?> {
    val countByType = sections.groupingBy { it.type }.eachCount()
    val seen = mutableMapOf<SectionType, Int>()
    return sections.associate { section ->
        val n = seen.getOrElse(section.type) { 0 } + 1
        seen[section.type] = n
        section.id to if (countByType.getValue(section.type) > 1) n else null
    }
}

/** A copy of this content in which the content and every nested element have new ids. */
fun SectionContent.deepCopy(ids: IdGenerator): SectionContent = copy(
    id = ids.newId(),
    lyricLines = lyricLines.map { line ->
        line.copy(
            id = ids.newId(),
            words = line.words.map { word ->
                word.copy(
                    id = ids.newId(),
                    syllables = word.syllables.map { it.copy(id = ids.newId()) },
                )
            },
        )
    },
    chords = chords.map { it.copy(id = ids.newId()) },
    tab = tab.map { it.copy(id = ids.newId()) },
)

/**
 * Pure editing operations on [Song]. Each returns a new song with `updatedAt = now`; the
 * input is never modified. Invalid section ids are ignored (the song is returned unchanged).
 */
object SongOps {
    const val MIN_SECTION_BARS: Int = 1
    const val MAX_SECTION_BARS: Int = 999
    const val DEFAULT_SECTION_BARS: Int = 4

    fun newSong(id: String, title: String, language: Language, now: Long): Song =
        Song(id = id, title = title, language = language, createdAt = now, updatedAt = now)

    /** A full copy of [song] under a new id and title. Nested ids are kept: they only need to be unique inside one song. */
    fun duplicateSong(song: Song, newId: String, newTitle: String, now: Long): Song =
        song.copy(id = newId, title = newTitle, createdAt = now, updatedAt = now)

    fun setTitle(song: Song, title: String, now: Long): Song =
        song.copy(title = title, updatedAt = now)

    /** A blank artist is stored as null. */
    fun setArtist(song: Song, artist: String, now: Long): Song =
        song.copy(artist = artist.ifBlank { null }, updatedAt = now)

    fun setLanguage(song: Song, language: Language, now: Long): Song =
        song.copy(language = language, updatedAt = now)

    /** Tempo is clamped to 30–300 BPM and rounded to 0.1. */
    fun setTempo(song: Song, bpm: Double, now: Long): Song = song.copy(
        tempoBpm = TapTempo.roundTempo(bpm.coerceIn(Song.MIN_TEMPO_BPM, Song.MAX_TEMPO_BPM)),
        updatedAt = now,
    )

    /**
     * Changes the time signature and keeps every section's number of bars, so section lengths
     * in beats follow the new bar size (e.g. 4 bars of 4/4 = 16 beats become 4 bars of 3/4 = 12 beats).
     */
    fun setTimeSignature(song: Song, timeSignature: TimeSignature, now: Long): Song {
        if (timeSignature == song.timeSignature) return song
        val old = song.timeSignature.beatsPerBar
        val contents = song.sectionContents.map { content ->
            val bars = (content.lengthBeats / old).coerceAtLeast(MIN_SECTION_BARS)
            content.copy(lengthBeats = bars * timeSignature.beatsPerBar)
        }
        return song.copy(timeSignature = timeSignature, sectionContents = contents, updatedAt = now)
    }

    /** Adds a new, independent section at [index] (default: the end). */
    fun addSection(
        song: Song,
        type: SectionType,
        bars: Int,
        label: String?,
        ids: IdGenerator,
        now: Long,
        index: Int = song.sections.size,
    ): Song {
        val content = SectionContent(
            id = ids.newId(),
            lengthBeats = clampBars(bars) * song.timeSignature.beatsPerBar,
        )
        val section = Section(id = ids.newId(), type = type, label = cleanLabel(label), contentId = content.id)
        val sections = song.sections.toMutableList().apply { add(index.coerceIn(0, size), section) }
        return song.copy(
            sections = sections,
            sectionContents = song.sectionContents + content,
            updatedAt = now,
        )
    }

    /**
     * Inserts a copy of a section right after it. A [linked] copy shares the original's content
     * (D-013: copies stay linked unless the user changes it); an independent copy gets its own
     * deep copy of the content.
     */
    fun duplicateSection(song: Song, sectionId: String, linked: Boolean, ids: IdGenerator, now: Long): Song {
        val index = song.sections.indexOfFirst { it.id == sectionId }
        if (index < 0) return song
        val original = song.sections[index]
        var contents = song.sectionContents
        val contentId = if (linked) {
            original.contentId
        } else {
            val copy = song.contentOf(original).deepCopy(ids)
            contents = contents + copy
            copy.id
        }
        val duplicate = original.copy(id = ids.newId(), contentId = contentId)
        val sections = song.sections.toMutableList().apply { add(index + 1, duplicate) }
        return song.copy(sections = sections, sectionContents = contents, updatedAt = now)
    }

    /** Gives a linked section its own copy of the content. Does nothing if it is not linked. */
    fun unlinkSection(song: Song, sectionId: String, ids: IdGenerator, now: Long): Song {
        val section = song.sections.firstOrNull { it.id == sectionId } ?: return song
        if (song.linkedCount(section) < 2) return song
        val copy = song.contentOf(section).deepCopy(ids)
        return song.copy(
            sections = song.sections.map { if (it.id == sectionId) it.copy(contentId = copy.id) else it },
            sectionContents = song.sectionContents + copy,
            updatedAt = now,
        )
    }

    /** Removes a section; its content is removed too when no other section uses it. */
    fun deleteSection(song: Song, sectionId: String, now: Long): Song {
        if (song.sections.none { it.id == sectionId }) return song
        val sections = song.sections.filterNot { it.id == sectionId }
        return song.copy(
            sections = sections,
            sectionContents = pruneContents(song.sectionContents, sections),
            updatedAt = now,
        )
    }

    /** Moves the section at [fromIndex] to [toIndex]; other sections shift. Out-of-range indices change nothing. */
    fun moveSection(song: Song, fromIndex: Int, toIndex: Int, now: Long): Song {
        val n = song.sections.size
        if (fromIndex !in 0 until n || toIndex !in 0 until n || fromIndex == toIndex) return song
        val sections = song.sections.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        return song.copy(sections = sections, updatedAt = now)
    }

    fun setSectionType(song: Song, sectionId: String, type: SectionType, now: Long): Song =
        updateSection(song, sectionId, now) { it.copy(type = type) }

    /** A blank label is stored as null (the type name is shown instead). */
    fun setSectionLabel(song: Song, sectionId: String, label: String?, now: Long): Song =
        updateSection(song, sectionId, now) { it.copy(label = cleanLabel(label)) }

    /** Sets the length in bars (1–999). Linked copies share the content, so they change too. */
    fun setSectionBars(song: Song, sectionId: String, bars: Int, now: Long): Song {
        val section = song.sections.firstOrNull { it.id == sectionId } ?: return song
        val beats = clampBars(bars) * song.timeSignature.beatsPerBar
        return song.copy(
            sectionContents = song.sectionContents.map {
                if (it.id == section.contentId) it.copy(lengthBeats = beats) else it
            },
            updatedAt = now,
        )
    }

    private fun updateSection(song: Song, sectionId: String, now: Long, change: (Section) -> Section): Song {
        if (song.sections.none { it.id == sectionId }) return song
        return song.copy(
            sections = song.sections.map { if (it.id == sectionId) change(it) else it },
            updatedAt = now,
        )
    }

    private fun pruneContents(contents: List<SectionContent>, sections: List<Section>): List<SectionContent> {
        val used = sections.mapTo(HashSet()) { it.contentId }
        return contents.filter { it.id in used }
    }

    private fun clampBars(bars: Int): Int = bars.coerceIn(MIN_SECTION_BARS, MAX_SECTION_BARS)

    private fun cleanLabel(label: String?): String? = label?.trim()?.ifEmpty { null }
}
