package com.deciboost.core.audio.android

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.deciboost.core.audio.policy.BoostEngine
import com.deciboost.core.audio.policy.BoostPercent
import com.deciboost.core.audio.policy.BoostState
import com.deciboost.core.audio.policy.ConfigSnapshot
import com.deciboost.core.audio.policy.GainMapper
import com.deciboost.core.audio.policy.NonMediaPlaybackGuard
import com.deciboost.core.audio.policy.PlaybackActivityTracker
import com.deciboost.core.audio.policy.PlaybackPhase
import com.deciboost.core.audio.policy.ReapplyReason
import com.deciboost.core.audio.policy.SessionEffectRegistry
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

class BoostEngineImpl(
    private val effectFactory: com.deciboost.core.audio.policy.AudioEffectFactory,
    private val playbackMonitor: PlaybackSessionMonitor,
    private val deviceMonitor: OutputDeviceMonitor,
    private val watchdog: BoostWatchdog,
    private val nonMediaGuard: NonMediaPlaybackGuard = NonMediaPlaybackGuard(),
    private val onEffectiveGainLearned: ((Int) -> Unit)? = null,
    private val foregroundChecker: () -> Boolean = { true },
    private var onStateChanged: ((BoostState) -> Unit)? = null,
) : BoostEngine {

    private val engineThread = HandlerThread("DeciBoost-Engine").apply { start() }
    private val engineHandler = Handler(engineThread.looper)

    private val registry = SessionEffectRegistry(effectFactory)
    private var currentBoost = BoostPercent(100)
    private var running = false
    private var pauseOnNonMedia = true
    private var suspendedForNonMedia = false
    private var lastReapplyReason = ReapplyReason.SERVICE_START
    private var consecutiveWatchdogFailures = 0
    private var watchdogRecoveryInProgress = false
    private var lastPlaybackConfigs: List<ConfigSnapshot> = emptyList()
    private var skipNextPlaybackActiveReapply = false

    private val stateRef = AtomicReference(BoostState())
    private val effectHealthRef = AtomicReference(EffectHealthSnapshot())

    private var idleShutdownListener: ((Int) -> Unit)? = null

    private val activityTracker = PlaybackActivityTracker(
        onReapply = { reason ->
            if (reason == ReapplyReason.PLAYBACK_ACTIVE && skipNextPlaybackActiveReapply) {
                skipNextPlaybackActiveReapply = false
                return@PlaybackActivityTracker
            }
            forceReapply(reason)
        },
        onReleaseAndRecreate = { releaseAndRecreateGlobalEffects() },
        onIdleShutdown = { boostPercent -> idleShutdownListener?.invoke(boostPercent) },
    )

    override val playbackPhase: StateFlow<PlaybackPhase> = activityTracker.phase

    init {
        playbackMonitor.listener = object : PlaybackSessionMonitor.Listener {
            override fun onMusicActiveChanged(isMusicActive: Boolean) {
                engineHandler.post {
                    if (isMusicActive) {
                        val guardAction = evaluateNonMediaGuard(
                            lastPlaybackConfigs,
                            isMusicActive = true,
                        )
                        skipNextPlaybackActiveReapply =
                            guardAction == NonMediaPlaybackGuard.GuardAction.Resume
                    }
                    activityTracker.onMusicActiveChanged(isMusicActive)
                }
            }

            override fun onConfigsChanged(configs: List<ConfigSnapshot>) {
                engineHandler.post {
                    lastPlaybackConfigs = configs
                    val snapshot = if (configs.isEmpty()) {
                        ConfigSnapshot(count = 0, usageHash = 0)
                    } else {
                        configs.last()
                    }
                    activityTracker.onConfigChanged(snapshot)
                    evaluateNonMediaGuard(configs)
                }
            }

            override fun onTick() {
                engineHandler.post { activityTracker.onTick() }
            }
        }

        deviceMonitor.onDeviceChanged = {
            engineHandler.post {
                activityTracker.onDeviceChanged()
            }
        }

        watchdog.verifier = {
            val state = stateRef.get()
            val health = effectHealthRef.get()
            val expectedEnabled = state.isEnabled && !state.isSuspendedForNonMedia
            val expectedMb = if (expectedEnabled) state.effectiveGainMilliBel else 0
            val fgsPromoted = foregroundChecker()
            val healthy = when {
                !running -> true
                !fgsPromoted && expectedEnabled -> false
                !expectedEnabled -> !health.globalEnabled
                else -> health.globalEnabled &&
                    health.globalTargetGainMb >= expectedMb - GAIN_TOLERANCE_MB
            }
            if (!healthy) {
                if (!watchdogRecoveryInProgress) {
                    watchdogRecoveryInProgress = true
                    consecutiveWatchdogFailures++
                    if (consecutiveWatchdogFailures >= WATCHDOG_FAILURES_BEFORE_FORCE_RECREATE) {
                        consecutiveWatchdogFailures = 0
                        engineHandler.post {
                            registry.releaseAndRecreate(SessionEffectRegistry.GLOBAL_SESSION)
                            if (currentBoost.value > 100 && !suspendedForNonMedia) {
                                applyBoostInternal(ReapplyReason.WATCHDOG, retryAttempt = 0)
                            } else {
                                watchdogRecoveryInProgress = false
                            }
                        }
                    } else {
                        engineHandler.post {
                            registry.releaseAndRecreate(SessionEffectRegistry.GLOBAL_SESSION)
                            applyBoostInternal(ReapplyReason.WATCHDOG, retryAttempt = 0)
                        }
                    }
                }
            } else {
                consecutiveWatchdogFailures = 0
                watchdogRecoveryInProgress = false
            }
            healthy
        }
    }

    fun setStateListener(listener: (BoostState) -> Unit) {
        onStateChanged = listener
    }

    fun setIdleShutdownListener(listener: ((Int) -> Unit)?) {
        idleShutdownListener = listener
    }

    override fun setPauseOnNonMedia(enabled: Boolean) {
        engineHandler.post {
            pauseOnNonMedia = enabled
            nonMediaGuard.setEnabled(enabled)
            if (!enabled && suspendedForNonMedia) {
                suspendedForNonMedia = false
                forceReapply(ReapplyReason.NON_MEDIA_RESUME)
            }
        }
    }

    override fun start() {
        engineHandler.post {
            if (running) return@post
            running = true
            activityTracker.setBoostPercent(currentBoost.value)
            activityTracker.onEngineStarted()
            playbackMonitor.start()
            deviceMonitor.start()
            watchdog.start()
            forceReapply(ReapplyReason.SERVICE_START)
        }
    }

    override fun stop() {
        engineHandler.post {
            if (!running) return@post
            running = false
            watchdog.stop()
            deviceMonitor.stop()
            playbackMonitor.stop()
            registry.releaseAll()
            effectHealthRef.set(EffectHealthSnapshot())
            updateState { it.copy(isEnabled = false, attachedSessionCount = 0, effectiveGainMilliBel = 0) }
        }
    }

    override fun setBoost(percent: BoostPercent) {
        engineHandler.post {
            currentBoost = percent
            activityTracker.setBoostPercent(percent.value)
            if (!running) {
                updateState {
                    it.copy(
                        boostPercent = percent,
                        isEnabled = false,
                        effectiveGainMilliBel = 0,
                        globalEffectHealthy = true,
                        isSuspendedForNonMedia = false,
                    )
                }
                return@post
            }
            forceReapply(ReapplyReason.USER_SLIDER)
        }
    }

    override fun getState(): BoostState = stateRef.get()

    override fun forceReapply(reason: ReapplyReason) {
        if (!running && reason != ReapplyReason.SERVICE_START) return
        engineHandler.post {
            if (shouldRecreateEffectsBeforeApply(reason)) {
                registry.releaseAndRecreate(SessionEffectRegistry.GLOBAL_SESSION)
            }
            applyBoostInternal(reason, retryAttempt = 0)
        }
    }

    /**
     * OEM stacks (e.g. MediaTek, Honor) can detach session-0 effects when YouTube tears down
     * and recreates its AudioTrack on pause/resume. Re-setting gain on a stale handle reports
     * success but has no audible effect — release+recreate before playback-driven reapplies.
     */
    private fun shouldRecreateEffectsBeforeApply(reason: ReapplyReason): Boolean =
        when (reason) {
            ReapplyReason.PLAYBACK_ACTIVE,
            ReapplyReason.PLAYBACK_CONFIG_CHANGED,
            ReapplyReason.WATCHDOG,
            ReapplyReason.NON_MEDIA_RESUME,
            -> true
            ReapplyReason.SERVICE_START,
            ReapplyReason.USER_SLIDER,
            -> currentBoost.value > 100
            else -> false
        }

    override fun releaseAndRecreateGlobalEffects() {
        engineHandler.post {
            registry.releaseAndRecreate(SessionEffectRegistry.GLOBAL_SESSION)
            if (currentBoost.value > 100 && !suspendedForNonMedia) {
                applyBoostInternal(ReapplyReason.DEVICE_CHANGED, retryAttempt = 0)
            }
        }
    }

    override fun getLastReapplyReason(): ReapplyReason = lastReapplyReason

    override fun setEffectiveMaxGainCap(milliBel: Int) {
        engineHandler.post { registry.setMaxGainMb(milliBel) }
    }

    private fun applyBoostInternal(reason: ReapplyReason, retryAttempt: Int) {
        lastReapplyReason = reason
        val enabled = currentBoost.value > 100 && !suspendedForNonMedia
        val requestedMb = if (enabled) GainMapper.toTargetGainMilliBel(currentBoost) else 0

        val globalEffects = registry.getOrCreate(SessionEffectRegistry.GLOBAL_SESSION)
        val leResult = GainDiscovery.applyGain(
            enhancer = globalEffects.loudnessEnhancer,
            gainMb = requestedMb,
            maxGainMb = registry.currentMaxGainMb(),
            enable = enabled,
        )
        if (leResult.effectiveGainMb < requestedMb && leResult.success) {
            registry.setMaxGainMb(leResult.effectiveGainMb)
            onEffectiveGainLearned?.invoke(leResult.effectiveGainMb)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && enabled) {
            val dpDb = GainMapper.toDynamicsGainDb(leResult.effectiveGainMb)
            registry.applyDynamicsGain(SessionEffectRegistry.GLOBAL_SESSION, dpDb, true)
        } else if (!enabled) {
            registry.applyDynamicsGain(SessionEffectRegistry.GLOBAL_SESSION, 0f, false)
        }

        updateEffectHealthSnapshot(enabled, leResult.effectiveGainMb)

        val healthy = verifyEffectsEnabled(leResult.effectiveGainMb, enabled)
        if (!healthy && retryAttempt < MAX_RETRY_ATTEMPTS) {
            engineHandler.postDelayed(
                { applyBoostInternal(reason, retryAttempt + 1) },
                RETRY_DELAY_MS * (retryAttempt + 1),
            )
            return
        }

        if (healthy) {
            activityTracker.markReapplySuccess()
        }

        if (reason == ReapplyReason.WATCHDOG) {
            watchdogRecoveryInProgress = false
        }

        updateState {
            it.copy(
                boostPercent = currentBoost,
                isEnabled = enabled,
                attachedSessionCount = registry.sessionCount(),
                lastReapplyReason = reason,
                globalEffectHealthy = healthy,
                effectiveGainMilliBel = if (enabled) leResult.effectiveGainMb else 0,
                isSuspendedForNonMedia = suspendedForNonMedia,
            )
        }
    }

    private fun evaluateNonMediaGuard(
        configs: List<ConfigSnapshot>,
        isMusicActive: Boolean = activityTracker.isMusicActive(),
    ): NonMediaPlaybackGuard.GuardAction {
        if (!pauseOnNonMedia) return NonMediaPlaybackGuard.GuardAction.None
        return when (nonMediaGuard.evaluate(configs, isMusicActive)) {
            NonMediaPlaybackGuard.GuardAction.Suspend -> {
                suspendedForNonMedia = true
                applyBoostInternal(ReapplyReason.NON_MEDIA_SUSPEND, retryAttempt = 0)
                NonMediaPlaybackGuard.GuardAction.Suspend
            }
            NonMediaPlaybackGuard.GuardAction.Resume -> {
                suspendedForNonMedia = false
                applyBoostInternal(ReapplyReason.NON_MEDIA_RESUME, retryAttempt = 0)
                NonMediaPlaybackGuard.GuardAction.Resume
            }
            NonMediaPlaybackGuard.GuardAction.None -> NonMediaPlaybackGuard.GuardAction.None
        }
    }

    private fun verifyEffectsEnabled(expectedMb: Int, expectedEnabled: Boolean): Boolean {
        if (!expectedEnabled) return true
        val health = effectHealthRef.get()
        if (!health.globalEnabled) return false
        if (expectedMb == 0) return true
        return health.globalTargetGainMb >= expectedMb - GAIN_TOLERANCE_MB
    }

    private fun updateEffectHealthSnapshot(enabled: Boolean, effectiveMb: Int) {
        effectHealthRef.set(
            EffectHealthSnapshot(
                globalEnabled = enabled && registry.isGlobalEnabled(),
                globalTargetGainMb = if (enabled) effectiveMb else 0,
            ),
        )
    }

    private fun updateState(transform: (BoostState) -> BoostState) {
        val newState = transform(stateRef.get())
        stateRef.set(newState)
        onStateChanged?.invoke(newState)
    }

    fun getRegistry(): SessionEffectRegistry = registry

    fun shutdown() {
        stop()
        engineThread.quitSafely()
    }

    private data class EffectHealthSnapshot(
        val globalEnabled: Boolean = false,
        val globalTargetGainMb: Int = 0,
    )

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 50L
        private const val GAIN_TOLERANCE_MB = 0
        private const val WATCHDOG_FAILURES_BEFORE_FORCE_RECREATE = 2
    }
}
