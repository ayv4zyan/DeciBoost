package com.deciboost.core.domain

import com.deciboost.core.audio.policy.BoostEngine
import com.deciboost.core.audio.policy.BoostPercent
import com.deciboost.core.audio.policy.BoostState
import com.deciboost.core.audio.policy.PlaybackPhase
import com.deciboost.core.audio.policy.ReapplyReason
import com.deciboost.core.data.BoostPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FakeBoostEngine : BoostEngine {
    private val phaseFlow = MutableStateFlow(PlaybackPhase.Idle)
    private var state = BoostState()
    val appliedBoosts = mutableListOf<Int>()
    var stateListener: ((BoostState) -> Unit)? = null

    override val playbackPhase = phaseFlow.asStateFlow()

    override fun start() = Unit

    override fun stop() = Unit

    override fun setBoost(percent: BoostPercent) {
        appliedBoosts.add(percent.value)
        state = state.copy(boostPercent = percent, isEnabled = percent.value > 100)
        stateListener?.invoke(state)
    }

    override fun getState(): BoostState = state

    override fun forceReapply(reason: ReapplyReason) = Unit

    override fun releaseAndRecreateGlobalEffects() = Unit

    override fun setPauseOnNonMedia(enabled: Boolean) = Unit

    override fun getLastReapplyReason(): ReapplyReason = ReapplyReason.USER_SLIDER

    override fun setEffectiveMaxGainCap(milliBel: Int) = Unit
}

class AsyncFakeBoostEngine(
    private val scope: CoroutineScope,
    private val applyDelayMs: Long = 1L,
) : BoostEngine {
    private val delegate = FakeBoostEngine()
    var stateListener: ((BoostState) -> Unit)? = null

    override val playbackPhase = delegate.playbackPhase
    val appliedBoosts: List<Int> get() = delegate.appliedBoosts

    override fun start() = delegate.start()

    override fun stop() = delegate.stop()

    override fun setBoost(percent: BoostPercent) {
        scope.launch {
            delay(applyDelayMs)
            delegate.setBoost(percent)
            stateListener?.invoke(delegate.getState())
        }
    }

    override fun getState(): BoostState = delegate.getState()

    override fun forceReapply(reason: ReapplyReason) = delegate.forceReapply(reason)

    override fun releaseAndRecreateGlobalEffects() = delegate.releaseAndRecreateGlobalEffects()

    override fun setPauseOnNonMedia(enabled: Boolean) = delegate.setPauseOnNonMedia(enabled)

    override fun getLastReapplyReason(): ReapplyReason = delegate.getLastReapplyReason()

    override fun setEffectiveMaxGainCap(milliBel: Int) = delegate.setEffectiveMaxGainCap(milliBel)
}

class FakeBoostPreferences : BoostPreferences {
    val currentBoostPercent: Int
        get() = boostPercentFlow.value
    private val boostPercentFlow = MutableStateFlow(150)
    private val volumePercentFlow = MutableStateFlow(100)
    private val autoStartFlow = MutableStateFlow(false)
    private val gradualFlow = MutableStateFlow(false)
    private val pauseOnNonMediaFlow = MutableStateFlow(true)
    private val killSwitchFlow = MutableStateFlow(false)
    private val onboardingFlow = MutableStateFlow(true)
    private val effectiveMaxGainFlow = MutableStateFlow(3000)
    private val safetyLevelsFlow = MutableStateFlow<Set<Int>>(emptySet())
    private val visualizerFlow = MutableStateFlow(false)

    override val boostPercent: Flow<Int> = boostPercentFlow.asStateFlow()
    override val volumePercent: Flow<Int> = volumePercentFlow.asStateFlow()
    override val autoStartOnBoot: Flow<Boolean> = autoStartFlow.asStateFlow()
    override val gradualBoost: Flow<Boolean> = gradualFlow.asStateFlow()
    override val pauseOnNonMedia: Flow<Boolean> = pauseOnNonMediaFlow.asStateFlow()
    override val killSwitchEnabled: Flow<Boolean> = killSwitchFlow.asStateFlow()
    override val onboardingComplete: Flow<Boolean> = onboardingFlow.asStateFlow()
    override val effectiveMaxGainMb: Flow<Int> = effectiveMaxGainFlow.asStateFlow()
    override val safetyAcknowledgedLevels: Flow<Set<Int>> = safetyLevelsFlow.asStateFlow()
    override val visualizerEnabled: Flow<Boolean> = visualizerFlow.asStateFlow()

    override suspend fun setBoostPercent(value: Int) {
        boostPercentFlow.value = value
    }

    override suspend fun setVolumePercent(value: Int) {
        volumePercentFlow.value = value
    }

    override suspend fun setAutoStartOnBoot(value: Boolean) {
        autoStartFlow.value = value
    }

    override suspend fun setGradualBoost(value: Boolean) {
        gradualFlow.value = value
    }

    override suspend fun setPauseOnNonMedia(value: Boolean) {
        pauseOnNonMediaFlow.value = value
    }

    override suspend fun setKillSwitchEnabled(value: Boolean) {
        killSwitchFlow.value = value
    }

    override suspend fun setOnboardingComplete(value: Boolean) {
        onboardingFlow.value = value
    }

    override suspend fun setEffectiveMaxGainMb(value: Int) {
        effectiveMaxGainFlow.value = value
    }

    override suspend fun setSafetyAcknowledgedLevels(levels: Set<Int>) {
        safetyLevelsFlow.value = levels
    }

    override suspend fun setVisualizerEnabled(value: Boolean) {
        visualizerFlow.value = value
    }

    override suspend fun migrateEffectiveMaxGainMbIfNeeded() = Unit
}

class FakeBoostServiceCoordinator : BoostServiceCoordinator {
    var stopCount = 0

    override suspend fun stopForegroundService() {
        stopCount++
    }
}

fun createController(
    engine: BoostEngine,
    preferences: FakeBoostPreferences = FakeBoostPreferences(),
    coordinator: FakeBoostServiceCoordinator = FakeBoostServiceCoordinator(),
): BoostController {
    val controller = BoostController(
        engine = engine,
        preferences = preferences,
        serviceCoordinator = coordinator,
    )
    when (engine) {
        is FakeBoostEngine -> engine.stateListener = { controller.publishState(it) }
        is AsyncFakeBoostEngine -> engine.stateListener = { controller.publishState(it) }
    }
    return controller
}