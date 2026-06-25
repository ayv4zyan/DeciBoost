package com.deciboost.app.service

import android.content.Context
import android.content.Intent
import com.deciboost.core.audio.android.BoostEngineImpl
import com.deciboost.core.domain.BoostServiceCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoostServiceCoordinatorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: BoostEngineImpl,
) : BoostServiceCoordinator {
    override suspend fun stopForegroundService() {
        engine.stop()
        context.stopService(Intent(context, BoostForegroundService::class.java))
    }
}
