package com.deciboost.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only receiver for instrumented test setup (e.g. seeding onboarding).
 * Not used in production flows.
 */
class DebugTestSetupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SEED_ONBOARDING) return
        val preferences = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugTestSetupEntryPoint::class.java,
        ).boostPreferences()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                preferences.setOnboardingComplete(true)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEED_ONBOARDING = "com.deciboost.DEBUG.SEED_ONBOARDING"
    }
}
