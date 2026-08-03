package com.deciboost.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deciboost.app.receiver.BootCompletedReceiver
import com.deciboost.app.service.BootRestoreActionReceiver
import com.deciboost.app.service.BootRestoreNotifier
import com.deciboost.app.service.BootRestoreTrampolineActivity
import com.deciboost.app.service.BoostForegroundService
import com.deciboost.app.service.BoostServiceClient
import com.deciboost.core.data.BoostPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    @Inject lateinit var serviceClient: BoostServiceClient

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val receiver = BootCompletedReceiver()
    private val serviceClassName = BoostForegroundService::class.java.name

    @Before
    fun setUp() {
        hiltRule.inject()
        ensureEngineIdle()
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        )
    }

    @After
    fun tearDown() {
        // Always tear down so a mid-test failure cannot leave BIND_AUTO_CREATE alive.
        ensureEngineIdle()
    }

    @Test
    fun bootReceiver_doesNotStartFgsAutomatically() = runBlocking {
        preferences.setAutoStartOnBoot(false)
        preferences.setBoostPercent(150)
        receiver.onReceive(context.applicationContext, bootCompletedIntent())
        Thread.sleep(800)
        assertFalse(
            "FGS must not start when auto-start-on-boot is disabled",
            isServiceRunning(serviceClassName),
        )
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
            isServiceRunning(serviceClassName),
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

        assertFalse(
            "Kill switch must prevent FGS from remaining/running after restore attempt",
            isServiceRunning(serviceClassName),
        )
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
        assertFalse(
            "Boot receiver must only notify — FGS must not auto-start",
            isServiceRunning(serviceClassName),
        )
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

    /**
     * Fully stop FGS before/after each test.
     *
     * [BoostServiceClient.ensureRunning] binds with [Context.BIND_AUTO_CREATE], so a prior
     * restore test can keep the service alive after a bare [Context.stopService]. Always
     * unbind via [BoostServiceClient.stopService], then force-stop with shell as backup,
     * and poll until [isServiceRunning] is false.
     */
    private fun ensureEngineIdle() = runBlocking {
        preferences.setOnboardingComplete(true)
        preferences.setKillSwitchEnabled(false)
        preferences.setBoostPercent(100)
        preferences.setAutoStartOnBoot(false)

        // Preferred path: release BIND_AUTO_CREATE + stop engine + stopService.
        serviceClient.stopService()
        serviceClient.releaseBinding()

        // Direct stop in case client path raced with a late bind.
        context.stopService(Intent(context, BoostForegroundService::class.java))

        // Shell backup — defeats sticky / leftover startService races.
        val component = "${context.packageName}/$serviceClassName"
        runCatching {
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("am stopservice $component")
                .close()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()

        val deadlineMs = System.currentTimeMillis() + SERVICE_STOP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadlineMs) {
            if (!isServiceRunning(serviceClassName)) {
                return@runBlocking
            }
            // Re-issue stop while polling — binding may re-create briefly.
            serviceClient.releaseBinding()
            context.stopService(Intent(context, BoostForegroundService::class.java))
            Thread.sleep(SERVICE_STOP_POLL_MS)
        }

        assertFalse(
            "FGS still running after ${SERVICE_STOP_TIMEOUT_MS}ms teardown " +
                "(package=${context.packageName}, service=$serviceClassName)",
            isServiceRunning(serviceClassName),
        )
    }

    private fun bootCompletedIntent(): Intent = Intent(Intent.ACTION_BOOT_COMPLETED)

    private fun isServiceRunning(serviceClassName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClassName }
    }

    companion object {
        private const val SERVICE_STOP_TIMEOUT_MS = 5_000L
        private const val SERVICE_STOP_POLL_MS = 100L
    }
}
