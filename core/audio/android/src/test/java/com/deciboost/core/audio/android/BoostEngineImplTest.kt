package com.deciboost.core.audio.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.deciboost.core.audio.policy.BoostPercent
import com.deciboost.core.audio.policy.ConfigSnapshot
import com.deciboost.core.audio.policy.ReapplyReason
import com.deciboost.core.audio.policy.SessionEffectRegistry
import com.deciboost.testing.fakes.FakeAudioEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class BoostEngineImplTest {

    @Test
    fun setBoost_whenNotRunning_resetsStateTo100() {
        val engine = createEngine()

        setBoostAndAwait(engine, 175)
        assertEquals(175, engine.getState().boostPercent.value)
        assertFalse(engine.getState().isEnabled)

        setBoostAndAwait(engine, 100)
        assertEquals(100, engine.getState().boostPercent.value)
        assertFalse(engine.getState().isEnabled)
        assertEquals(0, engine.getState().effectiveGainMilliBel)
    }

    @Test
    fun musicActiveAfterNonMediaSuspend_reenablesBoostWithoutNewConfig() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val playbackMonitor = PlaybackSessionMonitor(context, enableCallbacks = false)
        val engine = createEngine(playbackMonitor = playbackMonitor)

        engine.start()
        setBoostAndAwait(engine, 150)

        val notificationConfigs = listOf(
            ConfigSnapshot(1, 5, hasMediaUsage = false, hasNotificationUsage = true),
        )

        playbackMonitor.testDispatchMusicActiveChanged(false)
        playbackMonitor.testDispatchConfigsChanged(notificationConfigs)
        awaitState(engine, timeoutMs = 2000) { it.isSuspendedForNonMedia }

        playbackMonitor.testDispatchMusicActiveChanged(true)
        awaitState(engine, timeoutMs = 2000) { !it.isSuspendedForNonMedia && it.isEnabled }
        assertEquals(ReapplyReason.NON_MEDIA_RESUME, engine.getLastReapplyReason())
    }

    @Test
    fun watchdog_unhealthy_releasesBeforeReapply_andDefersFailureCountUntilRecoveryCompletes() {
        val factory = FakeAudioEffectFactory()
        // Long poll interval so only manual verifier invokes participate in this test.
        // Default 750ms races under CI load (recovery can release the enhancer before asserts).
        val watchdog = BoostWatchdog(pollIntervalMs = 60_000L)
        val engine = createEngine(
            effectFactory = factory,
            watchdog = watchdog,
            foregroundChecker = { false },
        )

        engine.start()
        setBoostAndAwait(engine, 150)
        watchdog.stop()

        val initialEnhancer = factory.loudnessEnhancers[SessionEffectRegistry.GLOBAL_SESSION]
        assertTrue("Expected global loudness enhancer after boost apply", initialEnhancer != null)
        assertFalse("Enhancer should still be live after boost apply", initialEnhancer!!.released)
        val createsAfterBoost = factory.loudnessCreateCount

        // First unhealthy tick schedules release+reapply on the engine thread. A second tick
        // while recoveryInProgress must not schedule another recovery (deferred failure count).
        watchdog.verifier?.invoke()
        watchdog.verifier?.invoke()

        awaitCondition(timeoutMs = 2_000) { initialEnhancer.released }
        assertEquals(ReapplyReason.WATCHDOG, engine.getLastReapplyReason())
        assertEquals(
            "Double verifier invoke during recovery must produce only one recreate",
            createsAfterBoost + 1,
            factory.loudnessCreateCount,
        )

        val recoveredEnhancer = factory.loudnessEnhancers[SessionEffectRegistry.GLOBAL_SESSION]
        assertTrue(recoveredEnhancer != null)
        assertFalse(recoveredEnhancer!!.released)

        // Recovery cleared recoveryInProgress; next unhealthy tick is the 2nd failure → force path.
        watchdog.verifier?.invoke()
        awaitCondition(timeoutMs = 2_000) { recoveredEnhancer.released }
        val forceRecoveredEnhancer = factory.loudnessEnhancers[SessionEffectRegistry.GLOBAL_SESSION]
        assertTrue(forceRecoveredEnhancer != null)
        assertFalse(forceRecoveredEnhancer!!.released)
        assertEquals(
            "Force recreate after second unhealthy cycle should create one more enhancer",
            createsAfterBoost + 2,
            factory.loudnessCreateCount,
        )
    }

    private fun createEngine(
        effectFactory: FakeAudioEffectFactory = FakeAudioEffectFactory(),
        playbackMonitor: PlaybackSessionMonitor = PlaybackSessionMonitor(
            ApplicationProvider.getApplicationContext(),
        ),
        watchdog: BoostWatchdog = BoostWatchdog(),
        foregroundChecker: () -> Boolean = { true },
    ): BoostEngineImpl {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return BoostEngineImpl(
            effectFactory = effectFactory,
            playbackMonitor = playbackMonitor,
            deviceMonitor = OutputDeviceMonitor(context),
            watchdog = watchdog,
            foregroundChecker = foregroundChecker,
        )
    }

    private fun setBoostAndAwait(engine: BoostEngineImpl, percent: Int) {
        val latch = CountDownLatch(1)
        engine.setStateListener { latch.countDown() }
        engine.setBoost(BoostPercent(percent))
        assertTrue(
            "Timed out waiting for engine handler to apply boost $percent",
            latch.await(500, TimeUnit.MILLISECONDS),
        )
    }

    private fun awaitState(
        engine: BoostEngineImpl,
        timeoutMs: Long = 1000,
        predicate: (com.deciboost.core.audio.policy.BoostState) -> Boolean,
    ) {
        awaitCondition(timeoutMs) { predicate(engine.getState()) }
    }

    private fun awaitCondition(timeoutMs: Long = 1000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) {
                return
            }
            Thread.sleep(20)
        }
        error("Timed out waiting for condition after ${timeoutMs}ms")
    }
}
