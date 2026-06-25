package com.deciboost.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.domain.BoostController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Transparent trampoline so boot-restore FGS start runs from a foreground Activity context (API 34+).
 */
@AndroidEntryPoint
class BootRestoreTrampolineActivity : AppCompatActivity() {

    @Inject lateinit var controller: BoostController
    @Inject lateinit var preferences: BoostPreferences
    @Inject lateinit var serviceClient: BoostServiceClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val boost = intent.getIntExtra(
            BootRestoreActionReceiver.EXTRA_BOOST,
            BootRestoreActionReceiver.DEFAULT_BOOST_PERCENT,
        )
        lifecycleScope.launch {
            try {
                BootRestoreHelper.restoreBoost(preferences, serviceClient, controller, boost)
            } catch (_: ForegroundServiceStartNotAllowedException) {
                BootRestoreNotifier.showFgsBlockedNotification(this@BootRestoreTrampolineActivity, boost)
            } finally {
                finish()
            }
        }
    }
}