package com.deciboost.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.domain.BoostController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class BootRestoreActionReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: BoostController
    @Inject lateinit var preferences: BoostPreferences
    @Inject lateinit var serviceClient: BoostServiceClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RESTORE_BOOST) return
        val boost = intent.getIntExtra(EXTRA_BOOST, DEFAULT_BOOST_PERCENT)
        val pending = goAsync()
        scope.launch {
            try {
                BootRestoreHelper.restoreBoost(preferences, serviceClient, controller, boost)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "FGS start blocked from background; prompting user to open app", e)
                BootRestoreNotifier.showFgsBlockedNotification(context, boost)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "Boot restore failed reading preferences", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Boot restore failed starting service", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootRestoreActionReceiver"
        const val ACTION_RESTORE_BOOST = "com.deciboost.ACTION_RESTORE_BOOST"
        const val EXTRA_BOOST = "boost"
        const val DEFAULT_BOOST_PERCENT = 100
    }
}
