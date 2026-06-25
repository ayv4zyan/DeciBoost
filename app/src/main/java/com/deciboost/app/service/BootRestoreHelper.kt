package com.deciboost.app.service

import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.domain.BoostController
import kotlinx.coroutines.flow.first

object BootRestoreHelper {

    enum class RestoreResult {
        Success,
        SkippedOnboarding,
        SkippedKillSwitch,
    }

    suspend fun restoreBoost(
        preferences: BoostPreferences,
        serviceClient: BoostServiceClient,
        controller: BoostController,
        boost: Int,
    ): RestoreResult {
        if (!preferences.onboardingComplete.first()) return RestoreResult.SkippedOnboarding
        if (preferences.killSwitchEnabled.first()) return RestoreResult.SkippedKillSwitch
        preferences.setBoostPercent(boost)
        serviceClient.ensureRunning().getOrThrow()
        controller.setBoostPercent(boost)
        return RestoreResult.Success
    }
}
