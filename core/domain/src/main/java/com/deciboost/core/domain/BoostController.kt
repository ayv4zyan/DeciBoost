package com.deciboost.core.domain

import com.deciboost.core.audio.policy.BoostEngine
import com.deciboost.core.audio.policy.BoostPercent
import com.deciboost.core.audio.policy.BoostState
import com.deciboost.core.data.BoostPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class BoostController(
    private val engine: BoostEngine,
    private val preferences: BoostPreferences,
    private val serviceCoordinator: BoostServiceCoordinator,
    private val onEngineReady: ((BoostEngine) -> Unit)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val boostMutex = Mutex()
    private var activeBoostJob: Job? = null
    private val _state = MutableStateFlow(engine.getState())
    val state: StateFlow<BoostState> = _state.asStateFlow()

    init {
        onEngineReady?.invoke(engine)
        scope.launch {
            preferences.pauseOnNonMedia.collect { enabled ->
                engine.setPauseOnNonMedia(enabled)
            }
        }
        scope.launch {
            preferences.killSwitchEnabled.collect { enabled ->
                if (enabled) {
                    activeBoostJob?.cancel()
                    activeBoostJob = null
                    preferences.setBoostPercent(MIN_BOOST_PERCENT)
                    engine.setBoost(BoostPercent(MIN_BOOST_PERCENT))
                    serviceCoordinator.stopForegroundService()
                }
            }
        }
        publishState(engine.getState())
    }

    fun publishState(state: BoostState) {
        _state.value = state
    }

    suspend fun setBoostPercent(percent: Int) {
        if (preferences.killSwitchEnabled.first()) return

        activeBoostJob?.cancelAndJoin()

        coroutineScope {
            val job = coroutineContext[Job]!!
            activeBoostJob = job
            try {
                boostMutex.withLock {
                    applyBoostPercentLocked(percent)
                }
            } finally {
                if (activeBoostJob == job) {
                    activeBoostJob = null
                }
            }
        }
    }

    private suspend fun applyBoostPercentLocked(percent: Int) {
        if (preferences.killSwitchEnabled.first()) return

        val target = percent.coerceIn(MIN_BOOST_PERCENT, MAX_BOOST_PERCENT)
        val gradual = preferences.gradualBoost.first()
        val current = preferences.boostPercent.first()

        if (gradual && abs(target - current) > GRADUAL_STEP_PERCENT) {
            var value = current
            val direction = if (target > current) 1 else -1
            while (value != target) {
                if (preferences.killSwitchEnabled.first()) return
                val delta = minOf(GRADUAL_STEP_PERCENT, abs(target - value))
                value += direction * delta
                preferences.setBoostPercent(value)
                engine.setBoost(BoostPercent(value))
                delay(GRADUAL_RAMP_DELAY_MS)
            }
        } else {
            preferences.setBoostPercent(target)
            engine.setBoost(BoostPercent(target))
        }
    }

    fun getState(): BoostState = engine.getState()

    suspend fun restoreFromPreferences() {
        if (preferences.killSwitchEnabled.first()) return
        val percent = preferences.boostPercent.first()
        engine.setBoost(BoostPercent(percent))
    }

    companion object {
        const val MIN_BOOST_PERCENT = 100
        const val MAX_BOOST_PERCENT = 200
        const val GRADUAL_STEP_PERCENT = 5
        const val GRADUAL_RAMP_DELAY_MS = 40L
    }
}