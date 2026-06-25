package com.deciboost.core.audio.policy

data class BoostState(
    val boostPercent: BoostPercent = BoostPercent(100),
    val isEnabled: Boolean = false,
    val attachedSessionCount: Int = 0,
    val lastReapplyReason: ReapplyReason = ReapplyReason.SERVICE_START,
    val globalEffectHealthy: Boolean = true,
    val effectiveGainMilliBel: Int = 0,
    val isSuspendedForNonMedia: Boolean = false,
)

enum class ReapplyReason {
    USER_SLIDER,
    PLAYBACK_ACTIVE,
    PLAYBACK_CONFIG_CHANGED,
    DEVICE_CHANGED,
    WATCHDOG,
    SERVICE_START,
    PROCESS_RESTART,
    NON_MEDIA_SUSPEND,
    NON_MEDIA_RESUME,
}