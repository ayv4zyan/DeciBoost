package com.deciboost.core.audio.policy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackActivityTracker(
    private val onReapply: (ReapplyReason) -> Unit,
    private val onReleaseAndRecreate: () -> Unit,
    private val onIdleShutdown: ((boostPercent: Int) -> Unit)? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _phase = MutableStateFlow(PlaybackPhase.Idle)
    val phase: StateFlow<PlaybackPhase> = _phase.asStateFlow()

    private var currentBoostPercent = 100
    private var lastConfig: ConfigSnapshot? = null
    private var lastMusicActive = false
    private var lastMusicInactiveAtMs: Long? = null
    private var lastConfigChangeAtMs: Long? = null
    private var recoveringStartedAtMs: Long? = null
    private var recoveringRetries = 0
    private var reapplyCount = 0
    private var lastIdleEligibleAtMs: Long? = null
    private var idleShutdownFired = false

    fun setBoostPercent(percent: Int) {
        currentBoostPercent = percent
        if (percent > 100 && _phase.value == PlaybackPhase.Idle) {
            transitionTo(PlaybackPhase.Active)
        }
        if (percent == 100) {
            if (lastIdleEligibleAtMs == null) {
                lastIdleEligibleAtMs = nowMs()
            }
        } else {
            lastIdleEligibleAtMs = null
            idleShutdownFired = false
        }
    }

    fun onMusicActiveChanged(isMusicActive: Boolean) {
        if (isMusicActive == lastMusicActive) return
        lastMusicActive = isMusicActive
        if (!isMusicActive) {
            lastMusicInactiveAtMs = nowMs()
            schedulePausedIfStable()
        } else {
            lastMusicInactiveAtMs = null
            handleMusicBecameActive()
        }
    }

    fun onConfigChanged(snapshot: ConfigSnapshot) {
        val configDiff = diff(lastConfig, snapshot)
        lastConfig = snapshot
        if (configDiff.kind == ConfigDiffKind.NONE) return

        val changedAt = nowMs()
        val previousChangeAt = lastConfigChangeAtMs
        if (previousChangeAt != null && changedAt - previousChangeAt < CONFIG_DEBOUNCE_MS) {
            return
        }
        lastConfigChangeAtMs = changedAt

        if (_phase.value == PlaybackPhase.Idle && snapshot.count > 0) {
            transitionTo(PlaybackPhase.Active)
        }

        if (currentBoostPercent > 100 && _phase.value == PlaybackPhase.Active) {
            triggerReapply(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
        }
    }

    fun onDeviceChanged() {
        onReleaseAndRecreate()
        if (currentBoostPercent > 100) {
            triggerReapply(ReapplyReason.DEVICE_CHANGED)
        }
    }

    fun onTick() {
        val now = nowMs()
        val inactiveSince = lastMusicInactiveAtMs
        if (!lastMusicActive && inactiveSince != null) {
            if (now - inactiveSince >= PAUSE_HOLD_MS &&
                _phase.value == PlaybackPhase.Active
            ) {
                transitionTo(PlaybackPhase.Paused)
            }
        }

        if (_phase.value == PlaybackPhase.Recovering) {
            val started = recoveringStartedAtMs ?: return
            if (now - started > RECOVERING_TIMEOUT_MS) {
                recoveringRetries++
                if (recoveringRetries >= MAX_RECOVERING_RETRIES) {
                    recoveringStartedAtMs = null
                    transitionTo(PlaybackPhase.Paused)
                } else {
                    recoveringStartedAtMs = now
                    triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                }
            }
        }

        if (currentBoostPercent == 100) {
            val idleStart = lastIdleEligibleAtMs
            if (idleStart != null && now - idleStart >= IDLE_TIMEOUT_MS) {
                if (_phase.value != PlaybackPhase.Idle) {
                    transitionTo(PlaybackPhase.Idle)
                }
                if (!idleShutdownFired) {
                    idleShutdownFired = true
                    onIdleShutdown?.invoke(currentBoostPercent)
                }
            }
        }
    }

    fun markReapplySuccess() {
        if (_phase.value == PlaybackPhase.Recovering) {
            recoveringRetries = 0
            recoveringStartedAtMs = null
            transitionTo(PlaybackPhase.Active)
        }
    }

    fun reapplyCount(): Int = reapplyCount

    fun resetReapplyCount() {
        reapplyCount = 0
    }

    /** Re-arms the idle shutdown timer when the engine/service restarts at boost 100%. */
    fun onEngineStarted() {
        idleShutdownFired = false
        if (currentBoostPercent == 100) {
            lastIdleEligibleAtMs = nowMs()
        }
    }

    private fun schedulePausedIfStable() {
        // Actual transition handled in onTick after PAUSE_HOLD_MS
    }

    private fun handleMusicBecameActive() {
        when (_phase.value) {
            PlaybackPhase.Paused -> {
                if (currentBoostPercent > 100) {
                    transitionTo(PlaybackPhase.Recovering)
                    recoveringStartedAtMs = nowMs()
                    recoveringRetries = 0
                    triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                } else {
                    transitionTo(PlaybackPhase.Active)
                }
            }
            PlaybackPhase.Idle -> {
                if (snapshotIndicatesActivity()) {
                    transitionTo(PlaybackPhase.Active)
                    if (currentBoostPercent > 100) {
                        triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                    }
                }
            }
            else -> {
                transitionTo(PlaybackPhase.Active)
                if (currentBoostPercent > 100) {
                    triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                }
            }
        }
    }

    private fun snapshotIndicatesActivity(): Boolean =
        (lastConfig?.count ?: 0) > 0 || lastMusicActive

    private fun transitionTo(phase: PlaybackPhase) {
        _phase.value = phase
    }

    private fun triggerReapply(reason: ReapplyReason) {
        reapplyCount++
        onReapply(reason)
    }

    companion object {
        const val PAUSE_HOLD_MS = 200L
        const val CONFIG_DEBOUNCE_MS = 100L
        const val MAX_RECOVERING_RETRIES = 3
        const val RECOVERING_TIMEOUT_MS = 1500L
        const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
