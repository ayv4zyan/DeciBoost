package com.deciboost.core.audio.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GainDiscoveryLogicTest {

    @Test
    fun `applyGain disables without clamping`() {
        val enhancer = ThresholdFakeEnhancer(rejectAboveMb = 0)
        val result = GainDiscoveryLogic.applyGain(enhancer, gainMb = 1500, maxGainMb = 3000, enable = false)
        assertTrue(result.success)
        assertEquals(0, result.effectiveGainMb)
        assertFalse(enhancer.enabled)
    }

    @Test
    fun `applyGain step down from rejected request`() {
        val enhancer = ThresholdFakeEnhancer(rejectAboveMb = 2000)
        val result = GainDiscoveryLogic.applyGain(enhancer, gainMb = 3000, maxGainMb = 3000, enable = true)
        assertTrue(result.success)
        assertEquals(2000, result.effectiveGainMb)
    }

    @Test
    fun `applyGain respects maxGainMb cap before discovery`() {
        val enhancer = ThresholdFakeEnhancer(rejectAboveMb = 3000)
        val result = GainDiscoveryLogic.applyGain(enhancer, gainMb = 3000, maxGainMb = 1800, enable = true)
        assertTrue(result.success)
        assertEquals(1800, result.effectiveGainMb)
    }

    @Test
    fun `applyGain returns failure when all steps rejected`() {
        val enhancer = ThresholdFakeEnhancer(rejectAboveMb = -1)
        val result = GainDiscoveryLogic.applyGain(enhancer, gainMb = 500, maxGainMb = 3000, enable = true)
        assertFalse(result.success)
        assertEquals(0, result.effectiveGainMb)
    }

    private class ThresholdFakeEnhancer(
        private val rejectAboveMb: Int,
    ) : LoudnessEnhancerHandle, GainTrackingHandle {
        override var enabled: Boolean = false
        override var lastGainMb: Int = 0

        override fun setTargetGain(milliBel: Int): Int {
            if (milliBel > rejectAboveMb) {
                return SessionEffectRegistry.ERROR_BAD_VALUE
            }
            lastGainMb = milliBel
            return 0
        }

        override fun release() {
            enabled = false
        }
    }
}
