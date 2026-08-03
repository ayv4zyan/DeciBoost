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
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val receiver = BootCompletedReceiver()
    private val serviceClassName = BoostForegroundService::class.java.name

    @Before
    fun setUp() {
        hiltRule.inject()
        ensureEngineIdle()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
        ).close()
    }

    @After
    fun tearDown() {
        // Mid-test failures must not leave BIND_AUTO_CREATE holding the FGS for the next test.
        ensureEngineIdle()
    }

    @Test
    fun bootReceiver_doesNotStartFgsAutomatically() = runBlocking {
        preferences.setAutoStartOnBoot(false)
        preferences.setBoostPercent(150)
        receiver.onReceive(context.applicationContext, bootCompletedIntent())
        // Allow goAsync IO work; FGS must never appear.
        assertTrue(
            "FGS must stay stopped when auto-start-on-boot is disabled",
            waitUntil(timeoutMs = ASYNC_SETTLE_TIMEOUT_MS) { !isServiceRunning(serviceClassName) },
        )
        // Hold briefly so a late async start would still be observed as a failure.
        assertFalse(
            "FGS must not start when auto-start-on-boot is disabled",
            waitUntilAppears(timeoutMs = NO_START_OBSERVE_MS) { isServiceRunning(serviceClassName) },
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

        assertTrue(
            "Expected FGS running after restore within ${SERVICE_START_TIMEOUT_MS}ms",
            waitUntil(timeoutMs = SERVICE_START_TIMEOUT_MS) { isServiceRunning(serviceClassName) },
        )
        val probe = waitForProbe(
            timeoutMs = SERVICE_START_TIMEOUT_MS,
            predicate = { it.globalEffectEnabled && it.targetGainMb > 0 },
        )
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
        assertFalse(
            "Expected no boot notification before onboarding",
            waitUntilAppears(timeoutMs = ASYNC_SETTLE_TIMEOUT_MS) { hasBootRestoreNotification() },
        )
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

        // Trampoline finishes quickly; ensure no late FGS start.
        assertFalse(
            "Kill switch must prevent FGS from starting after restore attempt",
            waitUntilAppears(timeoutMs = NO_START_OBSERVE_MS) { isServiceRunning(serviceClassName) },
        )
        val probe = waitForProbe(
            timeoutMs = ASYNC_SETTLE_TIMEOUT_MS,
            predicate = { !it.globalEffectEnabled && it.targetGainMb == 0 },
        )
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

        assertTrue(
            "Expected boot restore notification within ${ASYNC_SETTLE_TIMEOUT_MS}ms",
            waitUntil(timeoutMs = ASYNC_SETTLE_TIMEOUT_MS) { hasBootRestoreNotification() },
        )
        assertFalse(
            "Boot receiver must only notify — FGS must not auto-start",
            waitUntilAppears(timeoutMs = NO_START_OBSERVE_MS) { isServiceRunning(serviceClassName) },
        )

        val probe = waitForProbe(
            timeoutMs = ASYNC_SETTLE_TIMEOUT_MS,
            predicate = { !it.globalEffectEnabled && it.targetGainMb == 0 },
        )
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
     * restore test keeps the service alive after a bare [Context.stopService]. Always unbind via
     * [BoostServiceClient.stopService], re-issue stop while polling, and shell-stop as backup.
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

        // Shell backup — defeats sticky / leftover startService races (no uiautomator dep).
        val component = "${context.packageName}/$serviceClassName"
        runCatching {
            instrumentation.uiAutomation.executeShellCommand("am stopservice $component").close()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        BoostForegroundService.isForegroundPromoted = false

        val stopped = waitUntil(timeoutMs = SERVICE_STOP_TIMEOUT_MS) {
            if (!isServiceRunning(serviceClassName) && !BoostForegroundService.isForegroundPromoted) {
                return@waitUntil true
            }
            // Re-issue stop while polling — a pending bind may re-create briefly.
            serviceClient.releaseBinding()
            context.stopService(Intent(context, BoostForegroundService::class.java))
            false
        }
        assertTrue(
            "FGS still running after ${SERVICE_STOP_TIMEOUT_MS}ms teardown " +
                "(package=${context.packageName}, service=$serviceClassName, " +
                "promoted=${BoostForegroundService.isForegroundPromoted})",
            stopped,
        )
    }

    private fun bootCompletedIntent(): Intent = Intent(Intent.ACTION_BOOT_COMPLETED)

    private fun isServiceRunning(serviceClassName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClassName }
    }

    private fun hasBootRestoreNotification(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any { it.id == BootRestoreNotifier.BOOT_NOTIFICATION_ID }
    }

    /** Poll until [condition] is true or [timeoutMs] elapses. Returns last evaluation. */
    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    /**
     * Returns true if [condition] becomes true within [timeoutMs] (used to detect unwanted
     * side effects like a late FGS start).
     */
    private fun waitUntilAppears(timeoutMs: Long, condition: () -> Boolean): Boolean =
        waitUntil(timeoutMs, condition)

    private fun waitForProbe(
        timeoutMs: Long,
        predicate: (BoostProbeTestClient.Snapshot) -> Boolean,
    ): BoostProbeTestClient.Snapshot {
        var last = BoostProbeTestClient.dump(context)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(last)) return last
            Thread.sleep(POLL_MS)
            last = BoostProbeTestClient.dump(context)
        }
        return last
    }

    companion object {
        private const val SERVICE_STOP_TIMEOUT_MS = 5_000L
        private const val SERVICE_START_TIMEOUT_MS = 5_000L
        private const val ASYNC_SETTLE_TIMEOUT_MS = 3_000L
        /**
         * Observation window for asserting something does *not* start. Must cover trampoline
         * activity launch + restore coroutine (previously Thread.sleep(2000) in these tests).
         */
        private const val NO_START_OBSERVE_MS = 2_500L
        private const val POLL_MS = 100L
    }
}
