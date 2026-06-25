package com.deciboost.core.audio.policy

/**
 * Best-effort on anonymized AudioPlaybackConfiguration (usage + contentType only).
 * When pauseOnNonMedia=true and active configs contain USAGE_NOTIFICATION /
 * USAGE_ASSISTANCE / USAGE_ASSISTANT with no USAGE_MEDIA or CONTENT_TYPE_MUSIC,
 * temporarily set effective boost to 100% (effects disabled) until media resumes.
 */
class NonMediaPlaybackGuard(
    private var enabled: Boolean = true,
) {
    private var suspended = false

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled && suspended) {
            suspended = false
        }
    }

    fun shouldSuspendBoost(configs: List<ConfigSnapshot>, isMusicActive: Boolean = false): Boolean {
        if (isMusicActive) return false
        if (!enabled || configs.isEmpty()) return false
        val hasMedia = configs.any { it.hasMediaUsage }
        if (hasMedia) return false
        return configs.any { it.hasNotificationUsage || it.hasAssistanceUsage }
    }

    fun evaluate(configs: List<ConfigSnapshot>, isMusicActive: Boolean = false): GuardAction {
        val shouldSuspend = shouldSuspendBoost(configs, isMusicActive)
        return when {
            shouldSuspend && !suspended -> {
                suspended = true
                GuardAction.Suspend
            }
            !shouldSuspend && suspended -> {
                suspended = false
                GuardAction.Resume
            }
            else -> GuardAction.None
        }
    }

    fun isSuspended(): Boolean = suspended

    fun onSuspend() {
        suspended = true
    }

    fun onResume() {
        suspended = false
    }

    enum class GuardAction {
        None,
        Suspend,
        Resume,
    }
}
