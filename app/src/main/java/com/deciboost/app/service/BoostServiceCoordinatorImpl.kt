package com.deciboost.app.service

import com.deciboost.core.domain.BoostServiceCoordinator
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Stops the boost FGS through [BoostServiceClient] so any [android.content.ServiceConnection]
 * is released. A bare [android.content.Context.stopService] leaves a bound service alive
 * (common after [BoostServiceClient.ensureRunning]), which breaks kill-switch and tests.
 *
 * [Provider] breaks the Hilt cycle: Controller → Coordinator → Client → Controller.
 */
@Singleton
class BoostServiceCoordinatorImpl @Inject constructor(
    private val serviceClient: Provider<BoostServiceClient>,
) : BoostServiceCoordinator {
    override suspend fun stopForegroundService() {
        serviceClient.get().stopService()
    }
}
