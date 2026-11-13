package com.musicreader.core.model

import java.util.UUID

/** Creates unique ids for songs, sections and events. */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        /** Random UUIDs (letters, digits and dashes only, so they are safe in file names). */
        val RANDOM: IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    }
}
