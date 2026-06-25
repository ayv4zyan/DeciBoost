package com.deciboost.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deciboost.app.service.BootRestoreNotifier
import com.deciboost.core.data.BoostPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: BoostPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        scope.launch {
            try {
                val autoStart = preferences.autoStartOnBoot.first()
                val onboardingComplete = preferences.onboardingComplete.first()
                val savedBoost = preferences.boostPercent.first()
                if (autoStart && onboardingComplete && savedBoost > 100) {
                    BootRestoreNotifier.showRestoreNotification(context, savedBoost)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Boot receiver failed", e)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}