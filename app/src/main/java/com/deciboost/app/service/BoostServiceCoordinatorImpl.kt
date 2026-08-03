package com.deciboost.app.service

import android.content.Context
import android.content.Intent
import com.deciboost.core.audio.android.BoostEngineImpl
import com.deciboost.core.domain.BoostServiceCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class BoostServiceCoordinatorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: BoostEngineImpl,
    /**
     * Lazy to avoid a Hilt cycle:
     * Controller → Coordinator → Client → Controller.
     */
    private val serviceClient: Provider<BoostServiceClient>,
) : BoostServiceCoordinator {
    override suspend fun stopForegroundService() {
        // BIND_AUTO_CREATE from [BoostServiceClient.ensureRunning] keeps the service alive after
        // bare stopService — always release the bind first (kill switch / idle shutdown path).
        serviceClient.get().releaseBinding()
        engine.stop()
        context.stopService(Intent(context, BoostForegroundService::class.java))
    }
}
