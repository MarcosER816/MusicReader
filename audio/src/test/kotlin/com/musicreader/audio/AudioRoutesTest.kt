package com.musicreader.audio

import android.media.AudioDeviceInfo
import com.musicreader.core.model.AudioRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioRoutesTest {
    @Test
    fun deviceTypesMapToRoutes() {
        assertEquals(AudioRoute.SPEAKER, AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(AudioRoute.WIRED, AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioRoute.WIRED, AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(AudioRoute.USB, AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(AudioRoute.BLUETOOTH, AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(AudioRoute.BLUETOOTH, AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertNull(AudioRouteRules.routeForType(AudioDeviceInfo.TYPE_TELEPHONY))
    }

    @Test
    fun theStreamsDeviceWinsThenThePluggedInOne() {
        val all = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        assertEquals(AudioRoute.BLUETOOTH, AudioRouteRules.route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, all))
        assertEquals(AudioRoute.WIRED, AudioRouteRules.route(null, all))
        assertEquals(AudioRoute.BLUETOOTH, AudioRouteRules.route(null, all.dropLast(1)))
        assertEquals(AudioRoute.SPEAKER, AudioRouteRules.route(null, listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
        assertEquals(AudioRoute.SPEAKER, AudioRouteRules.route(null, emptyList()))
    }

    @Test
    fun gridMath() {
        val g = BeatGridInfo(anchorFrame = 1000, anchorBeat = 0, framesPerBeat = 24000.0, beatsPerBar = 3, barOriginBeat = 0)
        assertEquals(0.0, g.beatAt(1000.0))
        assertEquals(1.5, g.beatAt(37000.0))
        assertEquals(-0.5, g.beatAt(-11000.0))
        assertEquals(0, g.beatInBar(3))
        assertEquals(2, g.beatInBar(-1))
    }

    @Test
    fun diagnosticsDecode() {
        val a = DoubleArray(20)
        a[0] = 1.0; a[1] = 48000.0; a[3] = 96.0; a[4] = 192.0; a[6] = 0.0; a[7] = 12.0; a[8] = 2.0; a[9] = -1.0; a[15] = 12.5
        val d = EngineDiagnostics.fromArray(a)
        assertEquals(SharingMode.EXCLUSIVE, d.sharingMode)
        assertEquals(PerformanceMode.LOW_LATENCY, d.performanceMode)
        assertEquals(AudioApiName.AAUDIO, d.audioApi)
        assertNull(d.xRunCount)
        assertEquals(12.5, d.outputLatencyMs)
        assertEquals(4.0, d.framesToMs(192))
        val m = LatencyMeasurement.fromArray(doubleArrayOf(-1.0, 0.0, 0.0, 5.0, 0.0))
        assertNull(m.latencyMs)
    }
}
