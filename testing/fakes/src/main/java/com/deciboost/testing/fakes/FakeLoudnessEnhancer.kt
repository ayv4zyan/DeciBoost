package com.deciboost.testing.fakes

import com.deciboost.core.audio.policy.GainTrackingHandle
import com.deciboost.core.audio.policy.LoudnessEnhancerHandle
import com.deciboost.core.audio.policy.SessionEffectRegistry

class FakeLoudnessEnhancer(
    val sessionId: Int,
    private val maxGainMb: Int = 3000,
) : LoudnessEnhancerHandle, GainTrackingHandle {
    var released = false
        private set

    override var enabled: Boolean = false
    override var lastGainMb: Int = 0
        private set

    val gainHistory = mutableListOf<Int>()

    override fun setTargetGain(milliBel: Int): Int {
        if (released) return -1
        if (milliBel > maxGainMb) {
            return SessionEffectRegistry.ERROR_BAD_VALUE
        }
        lastGainMb = milliBel
        gainHistory.add(milliBel)
        return 0
    }

    override fun release() {
        released = true
        enabled = false
    }
}
