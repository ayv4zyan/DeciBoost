package com.deciboost.feature.boost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

class SystemVolumeController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    var onVolumeChanged: ((Int) -> Unit)? = null

    private var registered = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) {
                return
            }
            onVolumeChanged?.invoke(getVolumePercent())
        }
    }

    fun getVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun setVolumePercent(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = ((percent.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    fun startObserving() {
        if (registered) return
        registered = true
        appContext.registerReceiver(
            volumeReceiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    fun stopObserving() {
        if (!registered) return
        registered = false
        try {
            appContext.unregisterReceiver(volumeReceiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
