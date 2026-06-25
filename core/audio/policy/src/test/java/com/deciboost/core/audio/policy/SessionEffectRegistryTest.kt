package com.deciboost.core.audio.policy

import com.deciboost.testing.fakes.FakeAudioEffectFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionEffectRegistryTest {

    private lateinit var factory: FakeAudioEffectFactory
    private lateinit var registry: SessionEffectRegistry

    @Before
    fun setUp() {
        factory = FakeAudioEffectFactory()
        registry = SessionEffectRegistry(factory)
    }

    @Test
    fun `applyGain enables and sets target on global session`() {
        val result = registry.applyGain(SessionEffectRegistry.GLOBAL_SESSION, 1500, true)
        assertTrue(result.success)
        assertEquals(1500, result.effectiveGainMb)
        assertTrue(registry.isGlobalEnabled())
    }

    @Test
    fun `releaseAndRecreate creates fresh handle`() {
        registry.applyGain(SessionEffectRegistry.GLOBAL_SESSION, 1500, true)
        val old = factory.loudnessEnhancers[0]
        registry.releaseAndRecreate(SessionEffectRegistry.GLOBAL_SESSION)
        assertTrue(old?.released == true)
        assertTrue(factory.loudnessEnhancers[0] !== old)
    }

    @Test
    fun `applyGain clamps bad values`() {
        val limitedFactory = FakeAudioEffectFactory(maxGainMb = 2000)
        val limitedRegistry = SessionEffectRegistry(limitedFactory)
        val result = limitedRegistry.applyGain(SessionEffectRegistry.GLOBAL_SESSION, 3000, true)
        assertTrue(result.success)
        assertEquals(2000, result.effectiveGainMb)
    }

    @Test
    fun `setMaxGainMb seeds persisted OEM ceiling`() {
        registry.setMaxGainMb(1800)
        val result = registry.applyGain(SessionEffectRegistry.GLOBAL_SESSION, 3000, true)
        assertTrue(result.success)
        assertEquals(1800, result.effectiveGainMb)
    }
}
