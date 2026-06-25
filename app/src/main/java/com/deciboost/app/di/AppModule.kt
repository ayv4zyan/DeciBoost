package com.deciboost.app.di

import android.content.Context
import com.deciboost.app.service.BoostForegroundService
import com.deciboost.app.service.BoostServiceCoordinatorImpl
import com.deciboost.core.audio.android.AndroidAudioEffectFactory
import com.deciboost.core.audio.android.BoostEngineImpl
import com.deciboost.core.audio.android.BoostWatchdog
import com.deciboost.core.audio.android.OutputDeviceMonitor
import com.deciboost.core.audio.android.PlaybackSessionMonitor
import com.deciboost.core.audio.policy.BoostEngine
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.data.BoostPreferencesDataStore
import com.deciboost.core.domain.BoostController
import com.deciboost.core.domain.BoostServiceCoordinator
import com.deciboost.core.domain.ObserveActivePlaybackUseCase
import com.deciboost.core.domain.ObserveActivePlaybackUseCaseImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideBoostPreferences(
        @ApplicationContext context: Context,
    ): BoostPreferences = BoostPreferencesDataStore(context)

    @Provides
    @Singleton
    fun providePlaybackSessionMonitor(
        @ApplicationContext context: Context,
    ): PlaybackSessionMonitor = PlaybackSessionMonitor(context)

    @Provides
    @Singleton
    fun provideOutputDeviceMonitor(
        @ApplicationContext context: Context,
    ): OutputDeviceMonitor = OutputDeviceMonitor(context)

    @Provides
    @Singleton
    fun provideBoostWatchdog(): BoostWatchdog = BoostWatchdog()

    @Provides
    @Singleton
    fun provideBoostEngine(
        playbackMonitor: PlaybackSessionMonitor,
        deviceMonitor: OutputDeviceMonitor,
        watchdog: BoostWatchdog,
        preferences: BoostPreferences,
    ): BoostEngineImpl {
        val engine = BoostEngineImpl(
            effectFactory = AndroidAudioEffectFactory(),
            playbackMonitor = playbackMonitor,
            deviceMonitor = deviceMonitor,
            watchdog = watchdog,
            onEffectiveGainLearned = { learnedMb ->
                persistenceScope.launch {
                    preferences.setEffectiveMaxGainMb(learnedMb)
                }
            },
            foregroundChecker = { BoostForegroundService.isForegroundPromoted },
        )
        persistenceScope.launch {
            preferences.migrateEffectiveMaxGainMbIfNeeded()
            engine.setEffectiveMaxGainCap(preferences.effectiveMaxGainMb.first())
        }
        return engine
    }

    @Provides
    fun provideBoostEngineInterface(engine: BoostEngineImpl): BoostEngine = engine

    @Provides
    @Singleton
    fun provideBoostServiceCoordinator(
        impl: BoostServiceCoordinatorImpl,
    ): BoostServiceCoordinator = impl

    @Provides
    @Singleton
    fun provideObserveActivePlaybackUseCase(
        engine: BoostEngine,
    ): ObserveActivePlaybackUseCase = ObserveActivePlaybackUseCaseImpl(engine)

    @Provides
    @Singleton
    fun provideBoostController(
        engine: BoostEngineImpl,
        preferences: BoostPreferences,
        serviceCoordinator: BoostServiceCoordinator,
    ): BoostController {
        val controller = BoostController(
            engine = engine,
            preferences = preferences,
            serviceCoordinator = serviceCoordinator,
            onEngineReady = { _ ->
                engine.setIdleShutdownListener { boostPercent ->
                    if (boostPercent == 100 && engine.getState().boostPercent.value == 100) {
                        persistenceScope.launch {
                            if (engine.getState().boostPercent.value == 100) {
                                serviceCoordinator.stopForegroundService()
                            }
                        }
                    }
                }
            },
        )
        engine.setStateListener { controller.publishState(it) }
        return controller
    }
}
