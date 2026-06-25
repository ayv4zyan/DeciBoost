package com.deciboost.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.deciboost.app.receiver.BootCompletedReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deciboost.app.service.BootRestoreActionReceiver
import com.deciboost.app.service.BootRestoreNotifier
import com.deciboost.app.service.BootRestoreTrampolineActivity
import com.deciboost.app.service.BoostForegroundService
import com.deciboost.core.data.BoostPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BootReceiverFgsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var preferences: BoostPreferences

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val receiver = BootCompletedReceiver()

    @Before
    fun setUp() {
        hiltRule.inject()
        ensureEngineIdle()
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun bootReceiver_doesNotStartFgsAutomatically() = runBlocking {
        preferences.setAutoStartOnBoot(false)
        preferences.setBoostPercent(150)
        receiver.onReceive(context.applicationContext, bootCompletedIntent())
        Thread.sleep(800)
        assertFalse(isServiceRunning(BoostForegroundService::class.java.name))
    }

    @Test
    fun bootRestoreAction_startsFgsWithSavedBoost() = runBlocking {
        preferences.setOnboardingComplete(true)
        preferences.setKillSwitchEnabled(false)
        preferences.setBoostPercent(150)

        val intent = Intent(context, BootRestoreTrampolineActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BootRestoreActionReceiver.EXTRA_BOOST, 150)
        }
        context.startActivity(intent)
        Thread.sleep(2_000)

        assertTrue(
            "Expected FGS running after restore",
            isServiceRunning(BoostForegroundService::class.java.name),
        )
        val probe = BoostProbeTestClient.dump(context)
        assertTrue(
            "Expected global effect enabled after restore (gain=${probe.targetGainMb})",
            probe.globalEffectEnabled,
        )
        assertTrue(
            "Expected non-zero target gain after restore (was ${probe.targetGainMb})",
            probe.targetGainMb > 0,
        )
    }

    @Test
    fun bootReceiver_skipsNotification_whenOnboardingIncomplete() = runBlocking {
        preferences.setOnboardingComplete(false)
        preferences.setAutoStartOnBoot(true)
        preferences.setBoostPercent(150)
        receiver.onReceive(context.applicationContext, bootCompletedIntent())
        Thread.sleep(800)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasBootNotification = manager.activeNotifications.any {
            it.id == BootRestoreNotifier.BOOT_NOTIFICATION_ID
        }
        assertFalse("Expected no boot notification before onboarding", hasBootNotification)
    }

    @Test
    fun bootRestoreAction_skipsBoost_whenKillSwitchEnabled() = runBlocking {
        preferences.setOnboardingComplete(true)
        preferences.setKillSwitchEnabled(true)
        preferences.setBoostPercent(150)

        val intent = Intent(context, BootRestoreTrampolineActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(BootRestoreActionReceiver.EXTRA_BOOST, 150)
        }
        context.startActivity(intent)
        Thread.sleep(2_000)

        assertFalse(isServiceRunning(BoostForegroundService::class.java.name))
        val probe = BoostProbeTestClient.dump(context)
        assertFalse(
            "Kill switch must block restore boost (gain=${probe.targetGainMb})",
            probe.globalEffectEnabled,
        )
        assertTrue(
            "Target gain must be zero when kill switch blocks restore (was ${probe.targetGainMb})",
            probe.targetGainMb == 0,
        )
    }

    @Test
    fun bootReceiver_showsRestoreNotification_withoutStartingFgs() = runBlocking {
        preferences.setOnboardingComplete(true)
        preferences.setAutoStartOnBoot(true)
        preferences.setBoostPercent(150)
        receiver.onReceive(context.applicationContext, bootCompletedIntent())
        Thread.sleep(800)
        assertFalse(isServiceRunning(BoostForegroundService::class.java.name))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasBootNotification = manager.activeNotifications.any {
            it.id == BootRestoreNotifier.BOOT_NOTIFICATION_ID
        }
        assertTrue("Expected boot restore notification", hasBootNotification)

        val probe = BoostProbeTestClient.dump(context)
        assertFalse(
            "Boost must not be applied before user confirms restore notification " +
                "(gain=${probe.targetGainMb} enabled=${probe.globalEffectEnabled})",
            probe.globalEffectEnabled,
        )
        assertTrue(
            "Target gain must be zero before user confirms restore (was ${probe.targetGainMb})",
            probe.targetGainMb == 0,
        )
    }

    private fun ensureEngineIdle() = runBlocking {
        preferences.setOnboardingComplete(true)
        preferences.setKillSwitchEnabled(false)
        preferences.setBoostPercent(100)
        preferences.setAutoStartOnBoot(false)
        context.stopService(Intent(context, BoostForegroundService::class.java))
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        Thread.sleep(500)
    }

    private fun bootCompletedIntent(): Intent = Intent(Intent.ACTION_BOOT_COMPLETED)

    private fun isServiceRunning(serviceClassName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClassName }
    }
}
