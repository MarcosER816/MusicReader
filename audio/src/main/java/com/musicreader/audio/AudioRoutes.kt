package com.musicreader.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.musicreader.core.model.AudioRoute
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Maps Android output device types to the app's latency routes. */
object AudioRouteRules {
    fun routeForType(type: Int): AudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_AUX_LINE -> AudioRoute.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_DOCK -> AudioRoute.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID -> AudioRoute.BLUETOOTH
        else -> null
    }

    /**
     * The route of the stream's device ([deviceType], if known), else the one Android plays
     * media on: a plugged-in USB or wired device wins over Bluetooth, which wins over the speaker.
     */
    fun route(deviceType: Int?, connectedTypes: List<Int>): AudioRoute {
        deviceType?.let { routeForType(it) }?.let { return it }
        val routes = connectedTypes.mapNotNull(::routeForType).toSet()
        return listOf(AudioRoute.USB, AudioRoute.WIRED, AudioRoute.BLUETOOTH).firstOrNull { it in routes } ?: AudioRoute.SPEAKER
    }
}

/** Where audio is going, and when that may have changed. */
interface RouteSource {
    /** Route of the output device [deviceId] (0 = unknown). */
    fun routeOf(deviceId: Int): AudioRoute

    /** Emits when an output device is added or removed (headphones plugged or unplugged). */
    val changes: Flow<Unit>
}

class AndroidRouteSource(context: Context) : RouteSource {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    override fun routeOf(deviceId: Int): AudioRoute {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val type = devices.firstOrNull { deviceId != 0 && it.id == deviceId }?.type
        return AudioRouteRules.route(type, devices.map { it.type })
    }

    override val changes: Flow<Unit> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (added.any { it.isSink }) trySend(Unit)
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (removed.any { it.isSink }) trySend(Unit)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
}
