package com.deciboost.core.domain

import com.deciboost.core.audio.policy.BoostEngine
import com.deciboost.core.audio.policy.PlaybackPhase
import kotlinx.coroutines.flow.Flow

interface ObserveActivePlaybackUseCase {
    fun start()
    fun stop()
    val playbackPhase: Flow<PlaybackPhase>
}

/**
 * Observes playback phase only. Engine lifecycle is owned by [BoostForegroundService], not UI.
 */
class ObserveActivePlaybackUseCaseImpl(
    private val engine: BoostEngine,
) : ObserveActivePlaybackUseCase {
    override val playbackPhase: Flow<PlaybackPhase> = engine.playbackPhase

    override fun start() {
        // No-op: FGS / BoostServiceClient owns engine start/stop.
    }

    override fun stop() {
        // No-op: navigating away must not stop the singleton engine.
    }
}