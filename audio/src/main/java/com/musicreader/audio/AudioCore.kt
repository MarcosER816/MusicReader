package com.musicreader.audio

/**
 * Entry point to the native (C++17, Oboe) audio core.
 *
 * Stage 0 only proves the NDK build: Kotlin loads the native library and calls one JNI function.
 */
object AudioCore {
    init {
        System.loadLibrary("musicreader_audio")
    }

    /** Version text of the Oboe library linked into the native core, e.g. "Oboe 1.11.0". */
    fun oboeVersion(): String = nativeOboeVersion()

    private external fun nativeOboeVersion(): String
}
