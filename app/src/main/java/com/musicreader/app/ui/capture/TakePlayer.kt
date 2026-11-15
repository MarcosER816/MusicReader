package com.musicreader.app.ui.capture

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays a recorded take back (listening only; not musical timing). */
interface TakePlayer {
    fun play(samples: FloatArray, sampleRate: Int)
    fun stop()
}

class AudioTrackTakePlayer : TakePlayer {
    private var track: AudioTrack? = null

    override fun play(samples: FloatArray, sampleRate: Int) {
        stop()
        if (samples.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 4)
            .build()
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        t.play()
        track = t
    }

    override fun stop() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }
}
