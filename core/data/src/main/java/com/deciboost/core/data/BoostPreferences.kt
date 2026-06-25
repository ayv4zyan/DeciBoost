package com.deciboost.core.data

import kotlinx.coroutines.flow.Flow

interface BoostPreferences {
    val boostPercent: Flow<Int>
    val volumePercent: Flow<Int>
    val autoStartOnBoot: Flow<Boolean>
    val gradualBoost: Flow<Boolean>
    val pauseOnNonMedia: Flow<Boolean>
    val killSwitchEnabled: Flow<Boolean>
    val onboardingComplete: Flow<Boolean>
    val effectiveMaxGainMb: Flow<Int>
    val safetyAcknowledgedLevels: Flow<Set<Int>>
    val visualizerEnabled: Flow<Boolean>

    suspend fun setBoostPercent(value: Int)
    suspend fun setVolumePercent(value: Int)
    suspend fun setAutoStartOnBoot(value: Boolean)
    suspend fun setGradualBoost(value: Boolean)
    suspend fun setPauseOnNonMedia(value: Boolean)
    suspend fun setKillSwitchEnabled(value: Boolean)
    suspend fun setOnboardingComplete(value: Boolean)
    suspend fun setEffectiveMaxGainMb(value: Int)
    suspend fun setSafetyAcknowledgedLevels(levels: Set<Int>)
    suspend fun setVisualizerEnabled(value: Boolean)

    /** Copies legacy or orphaned fingerprint gain cap into the current device key if missing. */
    suspend fun migrateEffectiveMaxGainMbIfNeeded()
}
