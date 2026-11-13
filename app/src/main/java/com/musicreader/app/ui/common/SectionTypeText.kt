package com.musicreader.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.musicreader.app.R
import com.musicreader.core.model.SectionType

@StringRes
fun SectionType.nameRes(): Int = when (this) {
    SectionType.INTRO -> R.string.section_intro
    SectionType.VERSE -> R.string.section_verse
    SectionType.PRE_CHORUS -> R.string.section_pre_chorus
    SectionType.CHORUS -> R.string.section_chorus
    SectionType.BRIDGE -> R.string.section_bridge
    SectionType.OUTRO -> R.string.section_outro
}

@StringRes
fun SectionType.descriptionRes(): Int = when (this) {
    SectionType.INTRO -> R.string.section_intro_description
    SectionType.VERSE -> R.string.section_verse_description
    SectionType.PRE_CHORUS -> R.string.section_pre_chorus_description
    SectionType.CHORUS -> R.string.section_chorus_description
    SectionType.BRIDGE -> R.string.section_bridge_description
    SectionType.OUTRO -> R.string.section_outro_description
}

/** "Chorus 2", the custom label, or just "Intro" for a type that appears once. */
@Composable
fun sectionDisplayName(type: SectionType, label: String?, number: Int?): String {
    if (label != null) return label
    val name = stringResource(type.nameRes())
    return if (number != null) "$name $number" else name
}
