package com.deciboost.core.audio.android

import com.deciboost.core.audio.policy.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BoostWatchdog {
    var verifier: (() -> Boolean)? = null

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    var recoveryCount: Int = 0
        private set

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(FeatureFlags.WATCHDOG_INTERVAL_MS)
                val healthy = verifier?.invoke() ?: true
                if (!healthy) {
                    recoveryCount++
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}