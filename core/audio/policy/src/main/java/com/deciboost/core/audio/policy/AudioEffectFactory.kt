package com.deciboost.core.audio.policy

interface AudioEffectFactory {
    fun createLoudnessEnhancer(sessionId: Int): LoudnessEnhancerHandle
    fun createDynamicsProcessing(sessionId: Int): DynamicsProcessingHandle?
}

interface LoudnessEnhancerHandle {
    var enabled: Boolean
    fun setTargetGain(milliBel: Int): Int
    fun release()
}

interface DynamicsProcessingHandle {
    var enabled: Boolean
    fun setPostGain(db: Float): Int
    fun release()
}
