package com.musicreader.core.model

import kotlinx.serialization.Serializable

/** Section types, in the order they are offered in the app. OUTRO is shown as "Outro / Coda". */
@Serializable
enum class SectionType { INTRO, VERSE, PRE_CHORUS, CHORUS, BRIDGE, OUTRO }

/** One entry in the song timeline. Its music lives in the [SectionContent] named by [contentId]. */
@Serializable
data class Section(
    val id: String,
    val type: SectionType,
    /** Optional custom name, e.g. "Chorus 2". Null shows the type name. */
    val label: String? = null,
    val contentId: String,
)

/**
 * The music of a section. Several [Section]s may share one content (linked copies, D-013):
 * editing it changes all of them.
 */
@Serializable
data class SectionContent(
    val id: String,
    /** Always whole bars: a multiple of the song's `timeSignature.num`. */
    val lengthBeats: Int,
    /** One strumming pattern for the whole section (D-008). */
    val strum: StrumPattern? = null,
    val lyricLines: List<LyricLine> = emptyList(),
    val chords: List<ChordEvent> = emptyList(),
    val tab: List<TabEvent> = emptyList(),
) {
    /** Length in bars for the given time signature. */
    fun bars(timeSignature: TimeSignature): Int = lengthBeats / timeSignature.beatsPerBar
}
