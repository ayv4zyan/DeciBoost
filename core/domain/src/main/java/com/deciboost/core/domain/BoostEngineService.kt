package com.deciboost.core.domain

interface BoostEngineService {
    suspend fun ensureRunning(): Result<Unit>
    fun releaseBinding()
}