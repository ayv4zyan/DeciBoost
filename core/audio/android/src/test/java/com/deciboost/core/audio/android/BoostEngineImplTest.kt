package com.deciboost.core.audio.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.deciboost.core.audio.policy.BoostPercent
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = BoostEngineImpl(
            effectFactory = FakeAudioEffectFactory(),
            playbackMonitor = PlaybackSessionMonitor(context),
            deviceMonitor = OutputDeviceMonitor(context),
            watchdog = BoostWatchdog(),
        )

        setBoostAndAwait(engine, 175)
        assertEquals(175, engine.getState().boostPercent.value)
        assertFalse(engine.getState().isEnabled)

        setBoostAndAwait(engine, 100)
        assertEquals(100, engine.getState().boostPercent.value)
        assertFalse(engine.getState().isEnabled)
        assertEquals(0, engine.getState().effectiveGainMilliBel)
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
}