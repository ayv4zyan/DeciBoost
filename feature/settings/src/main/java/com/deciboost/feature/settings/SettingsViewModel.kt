package com.deciboost.feature.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deciboost.core.data.BoostPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val preferences: BoostPreferences,
) : ViewModel() {

    val autoStartOnBoot = preferences.autoStartOnBoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val gradualBoost = preferences.gradualBoost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pauseOnNonMedia = preferences.pauseOnNonMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val killSwitchEnabled = preferences.killSwitchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val visualizerEnabled = preferences.visualizerEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun setVisualizerEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setVisualizerEnabled(enabled)
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

    fun createBatteryOptimizationIntent(): Intent {
        val powerManager = application.getSystemService(Application.POWER_SERVICE) as PowerManager
        return if (powerManager.isIgnoringBatteryOptimizations(application.packageName)) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${application.packageName}")
            }
        }
    }
}
