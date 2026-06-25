package com.deciboost.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.deciboost.core.audio.android.BoostEngineImpl
import com.deciboost.core.audio.policy.BoostState
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.domain.BoostController
import com.deciboost.core.domain.BoostEngineService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface BoostServiceClient {
    suspend fun setBoost(percent: Int)
    fun observeState(): StateFlow<BoostState>
    suspend fun ensureRunning(): Result<Unit>
    suspend fun stopService(): Result<Unit>
    fun releaseBinding()
}

@Singleton
class BoostServiceClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: BoostController,
    private val engine: BoostEngineImpl,
    private val preferences: BoostPreferences,
) : BoostServiceClient, BoostEngineService {

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    override suspend fun setBoost(percent: Int) {
        if (preferences.killSwitchEnabled.first()) return
        ensureRunning()
        controller.setBoostPercent(percent)
    }

    override fun observeState(): StateFlow<BoostState> = controller.state

    override suspend fun ensureRunning(): Result<Unit> = runCatching {
        if (!preferences.onboardingComplete.first()) {
            throw OnboardingIncompleteException()
        }
        if (preferences.killSwitchEnabled.first()) return@runCatching
        engine.setEffectiveMaxGainCap(preferences.effectiveMaxGainMb.first())
        val intent = Intent(context, BoostForegroundService::class.java)
        context.startForegroundService(intent)
        if (!bound) {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override suspend fun stopService(): Result<Unit> = runCatching {
        releaseBinding()
        engine.stop()
        context.stopService(Intent(context, BoostForegroundService::class.java))
    }

    override fun releaseBinding() {
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Already unbound
            }
            bound = false
        }
    }
}