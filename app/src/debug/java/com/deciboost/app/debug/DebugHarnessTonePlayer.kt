package com.deciboost.app.debug

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays a looping sine wave in the target app process so [BoostEngineProbeImpl] Visualizer
 * can observe output-mix signal on emulators (test-process [HarnessActivity] audio is not
 * always visible to session-0 Visualizer in the app UID on API 36 AVDs).
 */
internal object DebugHarnessTonePlayer {

    private var audioTrack: AudioTrack? = null
    @Volatile private var playing = false
    private var playbackThread: Thread? = null

    @Synchronized
    fun start() {
        if (playing) return
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        playing = true
        track.play()
        playbackThread = Thread(
            {
                val frequency = 440.0
                val amplitude = 8000
                val frame = ShortArray(1024)
                var phase = 0.0
                while (playing) {
                    for (i in frame.indices) {
                        frame[i] = (Math.sin(phase) * amplitude).toInt().toShort()
                        phase += 2.0 * Math.PI * frequency / sampleRate
                    }
                    track.write(frame, 0, frame.size)
                }
            },
            "DebugHarnessTone",
        ).also { it.start() }
    }

    @Synchronized
    fun stop() {
        playing = false
        playbackThread?.join(1000)
        playbackThread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
