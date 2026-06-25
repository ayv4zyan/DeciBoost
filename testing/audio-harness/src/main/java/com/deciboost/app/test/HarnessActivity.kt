package com.deciboost.app.test

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle

/**
 * Plays a looping sine wave via AudioTrack for pause/resume harness tests.
 */
class HarnessActivity : Activity() {

    private var audioTrack: AudioTrack? = null
    @Volatile private var playing = false
    private var playbackThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        startPlayback()
    }

    override fun onResume() {
        super.onResume()
        instance = this
    }

    fun startPlayback() {
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
            "HarnessSine",
        ).also { it.start() }
    }

    fun pausePlayback() {
        audioTrack?.pause()
    }

    fun resumePlayback() {
        audioTrack?.play()
    }

    override fun onDestroy() {
        instance = null
        playing = false
        playbackThread?.join(1000)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: HarnessActivity? = null
    }
}
