package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatencyTest {
    private val wired = LatencyProfile(22.5, LatencyMethod.AUTOMATIC, 0.97, 1000)

    @Test
    fun oneProfilePerRoute() {
        var s = AppSettings()
        s = LatencyOps.save(s, AudioRoute.WIRED, wired)
        s = LatencyOps.save(s, AudioRoute.SPEAKER, LatencyProfile(40.0, LatencyMethod.MANUAL, 0.7, 2000))
        s = LatencyOps.save(s, AudioRoute.WIRED, wired.copy(roundTripMs = 21.0))  // measured again
        assertEquals(2, s.latencyProfiles.size)
        assertEquals(21.0, s.latencyProfiles.getValue(AudioRoute.WIRED).roundTripMs)
        s = LatencyOps.clear(s, AudioRoute.SPEAKER)
        assertEquals(setOf(AudioRoute.WIRED), s.latencyProfiles.keys)
    }

    @Test
    fun bluetoothIsAlwaysTooSlow() {
        assertTrue(LatencyOps.tooSlowForPractice(AudioRoute.BLUETOOTH, null))
        assertTrue(LatencyOps.tooSlowForPractice(AudioRoute.BLUETOOTH, wired))
        assertFalse(LatencyOps.tooSlowForPractice(AudioRoute.WIRED, wired))
        assertFalse(LatencyOps.tooSlowForPractice(AudioRoute.SPEAKER, null))
        assertTrue(LatencyOps.tooSlowForPractice(AudioRoute.USB, wired.copy(roundTripMs = 140.0)))
    }

    @Test
    fun profilesSurviveJson() {
        val s = LatencyOps.save(AppSettings(), AudioRoute.USB, wired)
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(AppSettings.serializer(), s)
        assertEquals(s, json.decodeFromString(AppSettings.serializer(), text))
        // Settings saved before Stage 7 had an empty map, which still reads.
        val old = json.decodeFromString(AppSettings.serializer(), """{"latencyProfiles":{}}""")
        assertEquals(emptyMap(), old.latencyProfiles)
    }
}
