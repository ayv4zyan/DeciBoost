package com.deciboost.core.audio.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread

class OutputDeviceMonitor(
    private val context: Context,
) {
    var onDeviceChanged: (() -> Unit)? = null

    private val monitorThread = HandlerThread("DeciBoost-DeviceMonitor").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var lastEventAtMs = 0L
    private var registered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) {
            scheduleDeviceChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
            scheduleDeviceChanged()
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            onDeviceChanged?.invoke()
        }
    }

    fun start() {
        monitorHandler.post {
            if (registered) return@post
            registered = true
            audioManager.registerAudioDeviceCallback(deviceCallback, monitorHandler)
            context.registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun stop() {
        monitorHandler.post {
            if (!registered) return@post
            registered = false
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            try {
                context.unregisterReceiver(becomingNoisyReceiver)
            } catch (_: IllegalArgumentException) {
                // already unregistered
            }
        }
    }

    private fun scheduleDeviceChanged() {
        val now = System.currentTimeMillis()
        if (now - lastEventAtMs < DEBOUNCE_MS) return
        lastEventAtMs = now
        onDeviceChanged?.invoke()
    }

    fun shutdown() {
        stop()
        monitorThread.quitSafely()
    }

    companion object {
        private const val DEBOUNCE_MS = 50L
    }
}