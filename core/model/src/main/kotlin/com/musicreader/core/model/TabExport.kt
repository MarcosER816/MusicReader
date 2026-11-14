package com.musicreader.core.model

/**
 * Text export of a section's tab, for debugging and sharing (Stage 5):
 * - alphaTex (the text format of alphaTab, https://alphatab.net), and
 * - plain ASCII tab.
 */
object TabExport {

    // --- alphaTex --------------------------------------------------------------------------------

    /**
     * alphaTex of a section: a header (title, tempo, time signature) then the beats as
     * `fret.string.duration` (several notes at once in parentheses), rests (`r`) for gaps and `|`
     * between bars. Effects: `{h}` hammer-on/pull-off, `{sl}` slide to the next note (`{sou}` /
     * `{sod}` out of the note when none follows), `{b (0 n)}` bend, `{b (0 n 0)}` bend and
     * release, `{v}` vibrato, `{nh}` / `{ah}` harmonics, `x` muted. Durations are note values
     * (4 = the time signature's beat in 4/4); a note keeps the largest note value that fits its
     * length and the rest of that length is written as rests.
     */
    fun alphaTex(title: String, content: SectionContent, timeSignature: TimeSignature, tempoBpm: Double): String {
        val sb = StringBuilder()
        sb.append("\\title \"").append(title.replace("\"", "'")).append("\"\n")
        sb.append("\\tempo ").append(tempoBpm.toInt()).append('\n')
        sb.append(".\n")
        sb.append("\\ts ").append(timeSignature.num).append(' ').append(timeSignature.den).append('\n')
        val tokens = mutableListOf<String>()
        timeline(content, timeSignature) { item ->
            when (item) {
                is Item.Bar -> tokens += "|"
                is Item.Rest -> tokens += "r.${item.value}"
                is Item.Notes -> {
                    val notes = item.notes.map { note -> alphaNote(note, content.tab) }
                    val body = if (notes.size == 1) notes.single() else notes.joinToString(" ", "(", ")")
                    tokens += "$body.${item.value}"
                }
            }
        }
        sb.append(tokens.joinToString(" ").trimEnd(' ', '|'))
        sb.append(" |\n")
        return sb.toString()
    }

    private fun alphaNote(note: TabEvent, tab: List<TabEvent>): String {
        val fret = if (note.technique == Technique.MUTE) "x" else note.fret.toString()
        val target = TabOps.legatoTarget(tab, note)
        val effect = when (note.technique) {
            Technique.NONE, Technique.MUTE -> null
            Technique.HAMMER_ON, Technique.PULL_OFF -> if (target != null) "h" else null
            Technique.SLIDE_UP -> if (target != null) "sl" else "sou"
            Technique.SLIDE_DOWN -> if (target != null) "sl" else "sod"
            Technique.BEND -> "b (0 ${note.bendQuarterTones ?: 4})"
            Technique.BEND_RELEASE -> "b (0 ${note.bendQuarterTones ?: 4} 0)"
            Technique.VIBRATO -> "v"
            Technique.NATURAL_HARMONIC -> "nh"
            Technique.ARTIFICIAL_HARMONIC -> "ah"
        }
        return "$fret.${note.string}" + (effect?.let { "{$it}" } ?: "")
    }

    private sealed interface Item {
        data object Bar : Item
        data class Rest(val value: Int) : Item
        data class Notes(val notes: List<TabEvent>, val value: Int) : Item
    }

    /**
     * Walks the section bar by bar: groups of notes on the same beat (lasting until the shortest
     * note ends or the next group starts), rests for gaps. Lengths are written as note values;
     * a group whose length is not one note value keeps the largest value that fits.
     */
    private fun timeline(content: SectionContent, ts: TimeSignature, emit: (Item) -> Unit) {
        val groups = content.tab.groupBy { it.beat }.toSortedMap()
        val beats = groups.keys.toList()
        val perBar = ts.beatsPerBar.toDouble()
        val end = content.lengthBeats.toDouble()
        var t = 0.0
        var index = 0
        while (t < end - 1e-9) {
            val barEnd = (kotlin.math.floor(t / perBar + 1e-9) + 1) * perBar
            val nextGroup = beats.getOrNull(index)
            if (nextGroup != null && kotlin.math.abs(nextGroup - t) < 1e-9) {
                val notes = groups.getValue(nextGroup)
                val following = beats.getOrNull(index + 1) ?: end
                val length = minOf(notes.minOf { it.durationBeats }, following - t, barEnd - t)
                val value = largestValue(length, ts.den)
                emit(Item.Notes(notes.sortedBy { it.string }, value))
                t += ts.den.toDouble() / value
                index++
            } else {
                val until = minOf(nextGroup ?: end, barEnd, end)
                val value = largestValue(until - t, ts.den)
                emit(Item.Rest(value))
                t += ts.den.toDouble() / value
            }
            if (t >= barEnd - 1e-9 && t < end - 1e-9) emit(Item.Bar)
        }
    }

    /** The largest note value (1, 2, 4 … 64) not longer than [beats] (a beat = 1/[den] note). */
    private fun largestValue(beats: Double, den: Int): Int {
        for (value in listOf(1, 2, 4, 8, 16, 32, 64)) {
            if (den.toDouble() / value <= beats + 1e-9) return value
        }
        return 64
    }

    // --- ASCII tab ------------------------------------------------------------------------------

    /**
     * Plain-text tab: a chord line, then six lines from high e to low E. One column per quarter
     * beat ([TabOps.GRID]), as wide as the widest label in it plus a dash; `|` at every bar.
     * Labels: fret with technique marks (h, p, /, \, b with the target fret or ¼, br, ~, <12>,
     * [12], x).
     */
    fun ascii(content: SectionContent, timeSignature: TimeSignature): String {
        val slots = (content.lengthBeats / TabOps.GRID).toInt().coerceAtLeast(1)
        val slotsPerBar = (timeSignature.beatsPerBar / TabOps.GRID).toInt()
        val labels = Array(6) { arrayOfNulls<String>(slots) }
        for (note in content.tab) {
            val slot = (note.beat / TabOps.GRID).toInt().coerceIn(0, slots - 1)
            labels[note.string - 1][slot] = asciiLabel(note)
        }
        val chordLabels = arrayOfNulls<String>(slots)
        for (chord in content.chords) {
            val slot = (chord.beat / TabOps.GRID).toInt().coerceIn(0, slots - 1)
            chordLabels[slot] = chord.symbol.toString()
        }
        val widths = IntArray(slots) { s -> (0 until 6).maxOf { labels[it][s]?.length ?: 1 } + 1 }

        val names = listOf("e", "B", "G", "D", "A", "E")
        val lines = (0 until 6).map { StringBuilder(names[it]).append('|') }
        val chordLine = StringBuilder("  ")
        for (s in 0 until slots) {
            if (s > 0 && s % slotsPerBar == 0) {
                lines.forEach { it.append('|') }
                chordLine.append(' ')
            }
            for (string in 0 until 6) {
                val label = labels[string][s] ?: "-"
                lines[string].append(label).append("-".repeat(widths[s] - label.length))
            }
            val chord = chordLabels[s]
            if (chord != null) {
                // A chord name may run into the next columns; it never shifts the tab.
                val room = widths[s]
                chordLine.append(chord.padEnd(room).take(maxOf(room, chord.length)))
            } else {
                chordLine.append(" ".repeat(widths[s]))
            }
        }
        lines.forEach { it.append('|') }
        val out = StringBuilder()
        if (content.chords.isNotEmpty()) out.append(chordLine.toString().trimEnd()).append('\n')
        lines.forEach { out.append(it).append('\n') }
        return out.toString()
    }

    private fun asciiLabel(note: TabEvent): String {
        val fret = note.fret
        return when (note.technique) {
            Technique.NONE -> "$fret"
            Technique.HAMMER_ON -> "${fret}h"
            Technique.PULL_OFF -> "${fret}p"
            Technique.SLIDE_UP -> "$fret/"
            Technique.SLIDE_DOWN -> "$fret\\"
            Technique.BEND -> "${fret}b${bendTarget(note)}"
            Technique.BEND_RELEASE -> "${fret}b${bendTarget(note)}r$fret"
            Technique.VIBRATO -> "$fret~"
            Technique.NATURAL_HARMONIC -> "<${note.harmonicFret ?: fret}>"
            Technique.ARTIFICIAL_HARMONIC -> "[${note.harmonicFret ?: fret}]"
            Technique.MUTE -> "x"
        }
    }

    /** A bend written as the fret it sounds like (7b9 = a full bend); a quarter-tone bend is "¼". */
    private fun bendTarget(note: TabEvent): String {
        val q = note.bendQuarterTones ?: 4
        return if (q % 2 == 0) "${note.fret + q / 2}" else "¼"
    }

    /** The amount of a bend as shown over its arrow: ¼, ½, full, or 1½ etc. */
    fun bendLabel(quarterTones: Int): String = when (quarterTones) {
        1 -> "¼"
        2 -> "½"
        3 -> "¾"
        4 -> "full"
        else -> if (quarterTones % 4 == 0) "${quarterTones / 4}" else "${quarterTones / 4}${bendLabel(quarterTones % 4)}"
    }
}
