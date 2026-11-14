package com.musicreader.app.ui

import com.musicreader.core.data.ChordDictionary
import java.io.File

/** The bundled chord dictionary, read from core/data's assets (unit tests run in the app module). */
object TestDictionary {
    val dictionary: ChordDictionary by lazy {
        ChordDictionary.parse(File("../core/data/src/main/assets/chords/guitar.json").readText())
    }
}
