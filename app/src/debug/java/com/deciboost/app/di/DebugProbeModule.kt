package com.deciboost.app.di

import com.deciboost.app.debug.BoostEngineProbe
import com.deciboost.app.debug.BoostEngineProbeImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugProbeModule {

    @Binds
    @Singleton
    abstract fun bindBoostEngineProbe(impl: BoostEngineProbeImpl): BoostEngineProbe
}
