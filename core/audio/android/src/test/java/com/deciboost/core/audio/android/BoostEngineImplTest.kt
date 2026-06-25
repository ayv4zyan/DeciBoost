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
        val watchdog = BoostWatchdog()
        val engine = createEngine(
            effectFactory = factory,
            watchdog = watchdog,
            foregroundChecker = { false },
        )

        engine.start()
        setBoostAndAwait(engine, 150)

        val initialEnhancer = factory.loudnessEnhancers[SessionEffectRegistry.GLOBAL_SESSION]
        assertTrue(initialEnhancer != null)

        watchdog.verifier?.invoke()
        assertFalse(initialEnhancer!!.released)
        watchdog.verifier?.invoke()
        assertFalse(initialEnhancer.released)

        drainEngineHandler(engine)
        assertEquals(ReapplyReason.WATCHDOG, engine.getLastReapplyReason())
        assertTrue(initialEnhancer.released)

        val recoveredEnhancer = factory.loudnessEnhancers[SessionEffectRegistry.GLOBAL_SESSION]
        assertTrue(recoveredEnhancer != null)
        assertFalse(recoveredEnhancer!!.released)

        watchdog.verifier?.invoke()
        drainEngineHandler(engine)
        val forceRecoveredEnhancer = factory.loudnessEnhancers[SessionEffectRegistry.GLOBAL_SESSION]
        assertTrue(forceRecoveredEnhancer != null)
        assertTrue(recoveredEnhancer.released)
        assertFalse(forceRecoveredEnhancer!!.released)
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

    private fun drainEngineHandler(@Suppress("UNUSED_PARAMETER") engine: BoostEngineImpl) {
        Thread.sleep(200)
    }

    private fun awaitState(
        engine: BoostEngineImpl,
        timeoutMs: Long = 1000,
        predicate: (com.deciboost.core.audio.policy.BoostState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(engine.getState())) {
                return
            }
            Thread.sleep(20)
        }
        error("Timed out waiting for engine state predicate")
    }
}
