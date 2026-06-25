package com.deciboost.testing.fakes

import com.deciboost.core.audio.policy.SessionEffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAudioEffectFactoryTest {

    @Test
    fun createLoudnessEnhancer_recordsGainCalls() {
        val factory = FakeAudioEffectFactory(maxGainMb = 3000)
        val enhancer = factory.createLoudnessEnhancer(0) as FakeLoudnessEnhancer

        assertEquals(0, enhancer.setTargetGain(1500))
        assertEquals(1500, enhancer.lastGainMb)
        assertEquals(listOf(1500), enhancer.gainHistory)
    }

    @Test
    fun createLoudnessEnhancer_rejectsGainAboveMax() {
        val factory = FakeAudioEffectFactory(maxGainMb = 1000)
        val enhancer = factory.createLoudnessEnhancer(0) as FakeLoudnessEnhancer

        assertEquals(SessionEffectRegistry.ERROR_BAD_VALUE, enhancer.setTargetGain(1500))
        assertEquals(0, enhancer.lastGainMb)
        assertTrue(enhancer.gainHistory.isEmpty())
    }

    @Test
    fun createLoudnessEnhancer_reusesLiveHandleForSession() {
        val factory = FakeAudioEffectFactory()
        val first = factory.createLoudnessEnhancer(0)
        val second = factory.createLoudnessEnhancer(0)

        assertSame(first, second)
    }

    @Test
    fun createLoudnessEnhancer_createsNewHandleAfterRelease() {
        val factory = FakeAudioEffectFactory()
        val first = factory.createLoudnessEnhancer(0) as FakeLoudnessEnhancer
        first.release()
        val second = factory.createLoudnessEnhancer(0)

        assertFalse(first === second)
        assertTrue(first.released)
    }
}
