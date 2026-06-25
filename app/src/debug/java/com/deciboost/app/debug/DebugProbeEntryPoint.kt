package com.deciboost.app.debug

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugProbeEntryPoint {
    fun boostEngineProbe(): BoostEngineProbe
}