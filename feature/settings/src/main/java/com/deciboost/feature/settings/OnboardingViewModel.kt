package com.deciboost.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deciboost.core.data.BoostPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingStatus(
    val isLoaded: Boolean = false,
    val isComplete: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: BoostPreferences,
) : ViewModel() {

    val onboardingStatus: StateFlow<OnboardingStatus> = preferences.onboardingComplete
        .map { OnboardingStatus(isLoaded = true, isComplete = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingStatus())

    val onboardingComplete = preferences.onboardingComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun completeOnboarding() = viewModelScope.launch {
        preferences.setOnboardingComplete(true)
    }
}
