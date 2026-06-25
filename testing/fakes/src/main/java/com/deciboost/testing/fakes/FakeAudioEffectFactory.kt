package com.deciboost.testing.fakes

import com.deciboost.core.audio.policy.AudioEffectFactory
import com.deciboost.core.audio.policy.DynamicsProcessingHandle
import com.deciboost.core.audio.policy.LoudnessEnhancerHandle

class FakeAudioEffectFactory(
    private val includeDynamics: Boolean = true,
    private val maxGainMb: Int = 3000,
) : AudioEffectFactory {
    val loudnessEnhancers = mutableMapOf<Int, FakeLoudnessEnhancer>()
    val dynamicsProcessors = mutableMapOf<Int, FakeDynamicsProcessing>()

    override fun createLoudnessEnhancer(sessionId: Int): LoudnessEnhancerHandle {
        val existing = loudnessEnhancers[sessionId]
        if (existing != null && !existing.released) return existing
        return FakeLoudnessEnhancer(sessionId, maxGainMb).also { loudnessEnhancers[sessionId] = it }
    }

    override fun createDynamicsProcessing(sessionId: Int): DynamicsProcessingHandle? {
        if (!includeDynamics) return null
        val existing = dynamicsProcessors[sessionId]
        if (existing != null && !existing.released) return existing
        return FakeDynamicsProcessing(sessionId).also { dynamicsProcessors[sessionId] = it }
    }
}
