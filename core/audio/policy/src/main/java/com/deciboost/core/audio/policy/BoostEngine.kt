package com.deciboost.core.audio.policy

import kotlinx.coroutines.flow.StateFlow

interface BoostEngine {
    val playbackPhase: StateFlow<PlaybackPhase>

    fun start()
    fun stop()
    fun setBoost(percent: BoostPercent)
    fun getState(): BoostState
    fun forceReapply(reason: ReapplyReason)
    fun releaseAndRecreateGlobalEffects()
    fun setPauseOnNonMedia(enabled: Boolean)
    fun getLastReapplyReason(): ReapplyReason
    fun setEffectiveMaxGainCap(milliBel: Int)
}
