package com.deciboost.app.debug

import com.deciboost.core.data.BoostPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugTestSetupEntryPoint {
    fun boostPreferences(): BoostPreferences
}