package com.deciboost.core.domain

interface BoostServiceCoordinator {
    suspend fun stopForegroundService()
}
