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
    private var pendingConfig: ConfigSnapshot? = null
    private var pendingConfigDeadlineMs: Long? = null
    private var activePhaseEnteredAtMs: Long? = null
    private var lastPeriodicRecreateAtMs: Long? = null
    private var settleReapplyAtMs: Long? = null
    private var lastOpaqueConfigReapplyAtMs: Long? = null

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
            settleReapplyAtMs = null
            schedulePausedIfStable()
        } else {
            // Keep lastMusicInactiveAtMs for RECENT_INACTIVE_WINDOW so opaque
            // same-fingerprint config events after pause still reapply.
            handleMusicBecameActive()
        }
    }

    fun onConfigChanged(snapshot: ConfigSnapshot) {
        val configDiff = diff(lastConfig, snapshot)
        if (configDiff.kind == ConfigDiffKind.NONE) {
            val pending = pendingConfig
            // Don't clobber a pending real change with a stale lastConfig echo.
            if (pending != null &&
                pending != snapshot &&
                diff(lastConfig, pending).kind != ConfigDiffKind.NONE
            ) {
                return
            }
            if (!shouldProcessOpaqueConfig()) {
                if (pending == null || snapshot == pending) {
                    pendingConfig = null
                    pendingConfigDeadlineMs = null
                }
                return
            }
        }

        pendingConfig = snapshot
        pendingConfigDeadlineMs = nowMs() + CONFIG_DEBOUNCE_MS
    }

    fun onDeviceChanged() {
        onReleaseAndRecreate()
    }

    fun isMusicActive(): Boolean = lastMusicActive

    internal fun lastConfigForTest(): ConfigSnapshot? = lastConfig

    fun onTick() {
        val now = nowMs()
        processPendingConfigIfDue(now)
        maybeSettleReapply(now)
        maybePeriodicRecreate(now)
        val inactiveSince = lastMusicInactiveAtMs
        if (!lastMusicActive && inactiveSince != null) {
            if (now - inactiveSince >= PAUSE_HOLD_MS &&
                _phase.value == PlaybackPhase.Active
            ) {
                transitionTo(PlaybackPhase.Paused)
            }
        }

        if (_phase.value == PlaybackPhase.Recovering) {
            val started = recoveringStartedAtMs
            if (started != null && now - started > RECOVERING_TIMEOUT_MS) {
                recoveringRetries++
                if (recoveringRetries >= MAX_RECOVERING_RETRIES) {
                    recoveringStartedAtMs = null
                    settleReapplyAtMs = null
                    onReleaseAndRecreate()
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
                    beginRecovering(ReapplyReason.PLAYBACK_ACTIVE)
                } else {
                    transitionTo(PlaybackPhase.Active)
                }
            }
            PlaybackPhase.Idle -> {
                if (snapshotIndicatesActivity()) {
                    transitionTo(PlaybackPhase.Active)
                    if (currentBoostPercent > 100) {
                        triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                        scheduleSettleReapply(nowMs())
                    }
                }
            }
            else -> {
                transitionTo(PlaybackPhase.Active)
                if (currentBoostPercent > 100) {
                    triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                    scheduleSettleReapply(nowMs())
                }
            }
        }
    }

    private fun snapshotIndicatesActivity(): Boolean =
        (lastConfig?.count ?: 0) > 0 || lastMusicActive

    private fun shouldProcessOpaqueConfig(): Boolean {
        if (currentBoostPercent <= 100) return false
        return when (_phase.value) {
            PlaybackPhase.Active,
            PlaybackPhase.Recovering,
            PlaybackPhase.Paused,
            -> true
            PlaybackPhase.Idle -> false
        }
    }

    private fun recentlyInactive(now: Long): Boolean {
        val inactiveAt = lastMusicInactiveAtMs ?: return false
        return now - inactiveAt <= RECENT_INACTIVE_WINDOW_MS
    }

    private fun processPendingConfigIfDue(now: Long) {
        val deadline = pendingConfigDeadlineMs ?: return
        if (now < deadline) return

        val snapshot = pendingConfig ?: return
        pendingConfig = null
        pendingConfigDeadlineMs = null

        val configDiff = diff(lastConfig, snapshot)
        lastConfig = snapshot
        lastConfigChangeAtMs = now

        if (currentBoostPercent <= 100) {
            if (_phase.value == PlaybackPhase.Idle && snapshot.count > 0) {
                transitionTo(PlaybackPhase.Active)
            }
            return
        }

        when (_phase.value) {
            PlaybackPhase.Idle -> {
                if (snapshot.count > 0) {
                    transitionTo(PlaybackPhase.Active)
                    triggerReapply(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
                }
            }
            PlaybackPhase.Paused -> {
                // DESIGN: Paused → recover when playback configs indicate a new/resumed session.
                if (snapshot.count > 0) {
                    beginRecovering(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
                }
            }
            PlaybackPhase.Recovering -> {
                if (snapshot.count > 0 || configDiff.kind != ConfigDiffKind.NONE) {
                    triggerReapply(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
                    scheduleSettleReapply(now)
                }
            }
            PlaybackPhase.Active -> {
                when {
                    configDiff.kind != ConfigDiffKind.NONE -> {
                        triggerReapply(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
                    }
                    // Same anonymized fingerprint but platform still notified us — common when
                    // ArcPlayer (etc.) tears down/recreates AudioTrack with identical usage.
                    // Always reapply after recent pause; otherwise throttle opaque events.
                    recentlyInactive(now) -> {
                        triggerReapply(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
                        lastOpaqueConfigReapplyAtMs = now
                    }
                    else -> {
                        val lastOpaque = lastOpaqueConfigReapplyAtMs
                        if (lastOpaque == null || now - lastOpaque >= OPAQUE_CONFIG_REAPPLY_MIN_INTERVAL_MS) {
                            triggerReapply(ReapplyReason.PLAYBACK_CONFIG_CHANGED)
                            lastOpaqueConfigReapplyAtMs = now
                        } else {
                            scheduleSettleReapply(now)
                        }
                    }
                }
            }
        }
    }

    private fun beginRecovering(reason: ReapplyReason) {
        transitionTo(PlaybackPhase.Recovering)
        recoveringStartedAtMs = nowMs()
        recoveringRetries = 0
        triggerReapply(reason)
        scheduleSettleReapply(nowMs())
    }

    private fun scheduleSettleReapply(now: Long) {
        if (currentBoostPercent <= 100) return
        val candidate = now + SETTLE_REAPPLY_MS
        val existing = settleReapplyAtMs
        // Keep the soonest pending settle so bursts don't push recovery out indefinitely.
        settleReapplyAtMs = if (existing == null) candidate else minOf(existing, candidate)
    }

    private fun maybeSettleReapply(now: Long) {
        val due = settleReapplyAtMs ?: return
        if (now < due) return
        settleReapplyAtMs = null
        if (currentBoostPercent <= 100) return
        when (_phase.value) {
            PlaybackPhase.Active,
            PlaybackPhase.Recovering,
            -> {
                if (lastMusicActive || _phase.value == PlaybackPhase.Recovering) {
                    triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
                }
            }
            else -> Unit
        }
    }

    private fun maybePeriodicRecreate(now: Long) {
        if (currentBoostPercent <= 100 || _phase.value != PlaybackPhase.Active) return

        val enteredAt = activePhaseEnteredAtMs ?: return
        val lastRecreate = lastPeriodicRecreateAtMs ?: enteredAt
        if (now - lastRecreate < ACTIVE_STALE_RECREATE_MS) return

        lastPeriodicRecreateAtMs = now
        triggerReapply(ReapplyReason.PLAYBACK_ACTIVE)
    }

    private fun transitionTo(phase: PlaybackPhase) {
        val previous = _phase.value
        _phase.value = phase
        if (phase == PlaybackPhase.Active && previous != PlaybackPhase.Active) {
            activePhaseEnteredAtMs = nowMs()
            lastPeriodicRecreateAtMs = null
        } else if (phase != PlaybackPhase.Active) {
            activePhaseEnteredAtMs = null
            lastPeriodicRecreateAtMs = null
        }
        if (phase == PlaybackPhase.Idle || phase == PlaybackPhase.Paused) {
            if (phase == PlaybackPhase.Paused && previous == PlaybackPhase.Recovering) {
                // Exhausted recovering — drop settle so we don't reapply while paused.
                settleReapplyAtMs = null
            }
            if (phase == PlaybackPhase.Idle) {
                settleReapplyAtMs = null
            }
        }
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
        const val ACTIVE_STALE_RECREATE_MS = 12 * 60 * 1000L
        /** Second reapply after resume so effects attach after the new AudioTrack exists. */
        const val SETTLE_REAPPLY_MS = 400L
        /** Window after music-inactive where same-fingerprint config events force reapply. */
        const val RECENT_INACTIVE_WINDOW_MS = 30_000L
        /** Throttle opaque (count+hash unchanged) config reapplies when not recently paused. */
        const val OPAQUE_CONFIG_REAPPLY_MIN_INTERVAL_MS = 500L
    }
}
