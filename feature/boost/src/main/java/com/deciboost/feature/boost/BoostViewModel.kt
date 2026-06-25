package com.deciboost.feature.boost

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deciboost.core.audio.policy.BoostState
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.domain.BoostController
import com.deciboost.core.domain.BoostEngineService
import com.deciboost.core.audio.policy.PlaybackPhase
import com.deciboost.core.domain.ObserveActivePlaybackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

data class SafetyDialogRequest(
    val level: Int,
    val message: String,
)

@HiltViewModel
class BoostViewModel @Inject constructor(
    private val application: Application,
    private val controller: BoostController,
    private val engineService: BoostEngineService,
    private val preferences: BoostPreferences,
    observePlayback: ObserveActivePlaybackUseCase,
) : ViewModel() {

    private val volumeController = SystemVolumeController(application)

    val boostState: StateFlow<BoostState> = controller.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), controller.getState())

    val playbackPhase: StateFlow<PlaybackPhase> = observePlayback.playbackPhase
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackPhase.Idle)

    val volumePercent: StateFlow<Int> = preferences.volumePercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val visualizerEnabled: StateFlow<Boolean> = preferences.visualizerEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val killSwitchEnabled: StateFlow<Boolean> = preferences.killSwitchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoStartOnBoot: StateFlow<Boolean> = preferences.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gradualBoost: StateFlow<Boolean> = preferences.gradualBoost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pauseOnNonMedia: StateFlow<Boolean> = preferences.pauseOnNonMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _outputDeviceLabel = MutableStateFlow("Detecting output…")
    val outputDeviceLabel: StateFlow<String> = _outputDeviceLabel.asStateFlow()

    private val _safetyDialog = MutableStateFlow<SafetyDialogRequest?>(null)
    val safetyDialog: StateFlow<SafetyDialogRequest?> = _safetyDialog.asStateFlow()

    private val _sliderPercent = MutableStateFlow(controller.getState().boostPercent.value)
    val sliderPercent: StateFlow<Int> = _sliderPercent.asStateFlow()

    private val safetyThresholds = listOf(
        101 to "Boosting above 100% may damage hearing and speakers. Continue?",
        175 to "High boost levels (175%+) significantly increase hearing risk.",
        200 to "Maximum boost (200%) — use only briefly and at your own risk.",
    )

    private val hapticDetents = setOf(125, 150, 175, 200)
    private var lastHapticDetent: Int? = null
    private var boostChangeJob: Job? = null

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            bindService()
            viewModelScope.launch { syncHardwareVolume() }
        }

        override fun onStop(owner: LifecycleOwner) {
            releaseServiceBinding()
        }
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        volumeController.onVolumeChanged = { percent ->
            viewModelScope.launch {
                preferences.setVolumePercent(percent)
            }
        }
        volumeController.startObserving()
        viewModelScope.launch {
            syncHardwareVolume()
        }
        viewModelScope.launch {
            if (preferences.onboardingComplete.first()) {
                engineService.ensureRunning()
                controller.restoreFromPreferences()
            }
        }
        viewModelScope.launch {
            syncVisualizerPermission()
        }
        viewModelScope.launch {
            boostState.collect { state ->
                if (_safetyDialog.value == null) {
                    _sliderPercent.value = state.boostPercent.value
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                _outputDeviceLabel.value = resolveCurrentOutputDevice(application).label
                delay(OUTPUT_DEVICE_POLL_INTERVAL_MS)
            }
        }
    }

    fun bindService() {
        viewModelScope.launch {
            if (preferences.onboardingComplete.first()) {
                engineService.ensureRunning()
            }
        }
    }

    fun releaseServiceBinding() {
        engineService.releaseBinding()
    }

    fun onBoostChanged(percent: Int) {
        if (killSwitchEnabled.value) return
        _sliderPercent.value = percent
        boostChangeJob?.cancel()
        boostChangeJob = viewModelScope.launch {
            val acknowledgedLevels = preferences.safetyAcknowledgedLevels.first()
            val nextThreshold = safetyThresholds.firstOrNull { (level, _) ->
                percent >= level && !acknowledgedLevels.contains(level)
            }
            if (nextThreshold != null) {
                _safetyDialog.value = SafetyDialogRequest(
                    level = nextThreshold.first,
                    message = nextThreshold.second,
                )
            } else {
                if (preferences.killSwitchEnabled.first()) return@launch
                if (preferences.onboardingComplete.first()) {
                    engineService.ensureRunning()
                }
                controller.setBoostPercent(percent)
            }
        }
    }

    fun shouldPerformHaptic(percent: Int): Boolean {
        if (_safetyDialog.value != null) return false
        val detent = hapticDetents.firstOrNull { abs(percent - it) <= 1 }
        if (detent != null && detent != lastHapticDetent) {
            lastHapticDetent = detent
            return true
        }
        if (detent == null) {
            lastHapticDetent = null
        }
        return false
    }

    fun onVolumeChanged(percent: Int) {
        viewModelScope.launch {
            val clamped = percent.coerceIn(0, 100)
            volumeController.setVolumePercent(clamped)
            preferences.setVolumePercent(clamped)
        }
    }

    fun confirmSafetyDialog() {
        val dialog = _safetyDialog.value ?: return
        viewModelScope.launch {
            val updated = preferences.safetyAcknowledgedLevels.first().toMutableSet()
            updated.add(dialog.level)
            preferences.setSafetyAcknowledgedLevels(updated)
            _safetyDialog.value = null
            onBoostChanged(_sliderPercent.value)
        }
    }

    fun dismissSafetyDialog() {
        _safetyDialog.value = null
        _sliderPercent.value = boostState.value.boostPercent.value
    }

    fun syncVisualizerPermission() {
        viewModelScope.launch {
            if (!preferences.visualizerEnabled.first()) return@launch
            val granted = ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                preferences.setVisualizerEnabled(false)
            }
        }
    }

    fun setAutoStartOnBoot(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoStartOnBoot(enabled)
    }

    fun setGradualBoost(enabled: Boolean) = viewModelScope.launch {
        preferences.setGradualBoost(enabled)
    }

    fun setPauseOnNonMedia(enabled: Boolean) = viewModelScope.launch {
        preferences.setPauseOnNonMedia(enabled)
    }

    fun setKillSwitchEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setKillSwitchEnabled(enabled)
    }

    override fun onCleared() {
        volumeController.stopObserving()
        volumeController.onVolumeChanged = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onCleared()
    }

    private suspend fun syncHardwareVolume() {
        preferences.setVolumePercent(volumeController.getVolumePercent())
    }

    private companion object {
        const val OUTPUT_DEVICE_POLL_INTERVAL_MS = 2_000L
    }
}
