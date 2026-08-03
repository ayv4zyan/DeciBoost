package com.deciboost.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deciboost.app.service.BootRestoreNotifier
import com.deciboost.core.data.BoostPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Shows a restore notification after boot when auto-start is enabled.
 *
 * Preferences are resolved via [EntryPointAccessors] so the receiver works both when
 * created by the system and when constructed directly in instrumented tests
 * (`BootCompletedReceiver().onReceive(...)`). Field `@Inject` would not run for the latter.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootCompletedReceiverEntryPoint::class.java,
        ).boostPreferences()

        val pending = goAsync()
        scope.launch {
            try {
                val autoStart = preferences.autoStartOnBoot.first()
                val onboardingComplete = preferences.onboardingComplete.first()
                val savedBoost = preferences.boostPercent.first()
                if (autoStart && onboardingComplete && savedBoost > 100) {
                    BootRestoreNotifier.showRestoreNotification(context, savedBoost)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Boot receiver failed reading preferences", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Boot receiver failed", e)
            } finally {
                // goAsync() is null when onReceive is invoked directly (instrumented tests).
                pending?.finish()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootCompletedReceiverEntryPoint {
        fun boostPreferences(): BoostPreferences
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
