package com.deciboost.core.audio.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.HandlerThread
import com.deciboost.core.audio.policy.ConfigSnapshot

class PlaybackSessionMonitor(
    context: Context,
) {
    interface Listener {
        fun onMusicActiveChanged(isMusicActive: Boolean)
        fun onConfigsChanged(configs: List<ConfigSnapshot>)
        fun onTick()
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val monitorThread = HandlerThread("DeciBoost-AudioMonitor").apply { start() }
    private val monitorHandler = Handler(monitorThread.looper)

    var listener: Listener? = null

    private var tickRunnable: Runnable? = null
    private var lastMusicActive = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            monitorHandler.post { dispatchConfigs(configs) }
        }
    }

    fun start() {
        monitorHandler.post {
            audioManager.registerAudioPlaybackCallback(playbackCallback, monitorHandler)
            lastMusicActive = audioManager.isMusicActive
            listener?.onMusicActiveChanged(lastMusicActive)
            dispatchConfigs(audioManager.activePlaybackConfigurations)
            startTicking()
        }
    }

    fun stop() {
        monitorHandler.post {
            tickRunnable?.let { monitorHandler.removeCallbacks(it) }
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        }
    }

    private fun startTicking() {
        val runnable = object : Runnable {
            override fun run() {
                val musicActive = audioManager.isMusicActive
                if (musicActive != lastMusicActive) {
                    lastMusicActive = musicActive
                    listener?.onMusicActiveChanged(musicActive)
                }
                listener?.onTick()
                monitorHandler.postDelayed(this, 50)
            }
        }
        tickRunnable = runnable
        monitorHandler.post(runnable)
    }

    private fun dispatchConfigs(configs: List<AudioPlaybackConfiguration>) {
        if (configs.isEmpty()) {
            listener?.onConfigsChanged(listOf(ConfigSnapshot(count = 0, usageHash = 0)))
            return
        }
        var usageHash = 0
        var hasMedia = false
        var hasNotification = false
        var hasAssistance = false
        configs.forEach { config ->
            val attrs = config.audioAttributes
            val usage = attrs.usage
            val contentType = attrs.contentType
            usageHash = usageHash xor (usage xor (contentType shl 8))
            hasMedia = hasMedia ||
                usage == AudioAttributes.USAGE_MEDIA ||
                contentType == AudioAttributes.CONTENT_TYPE_MUSIC
            hasNotification = hasNotification ||
                usage == AudioAttributes.USAGE_NOTIFICATION ||
                usage == AudioAttributes.USAGE_NOTIFICATION_EVENT ||
                usage == AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST
            hasAssistance = hasAssistance ||
                usage == AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY ||
                usage == AudioAttributes.USAGE_ASSISTANT ||
                usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
                usage == AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        }
        val snapshot = ConfigSnapshot(
            count = configs.size,
            usageHash = usageHash,
            hasMediaUsage = hasMedia,
            hasNotificationUsage = hasNotification,
            hasAssistanceUsage = hasAssistance,
        )
        listener?.onConfigsChanged(listOf(snapshot))
    }

    fun shutdown() {
        stop()
        monitorThread.quitSafely()
    }
}