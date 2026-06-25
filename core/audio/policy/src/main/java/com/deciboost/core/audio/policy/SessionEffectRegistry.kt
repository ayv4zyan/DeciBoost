package com.deciboost.core.audio.policy

class SessionEffectRegistry(private val factory: AudioEffectFactory) {
    companion object {
        const val GLOBAL_SESSION = 0
        const val ERROR_BAD_VALUE = 2
    }

    private val sessions = mutableMapOf<Int, SessionEffects>()
    private var maxGainMb: Int = GainMapper.MAX_GAIN_MB

    fun setMaxGainMb(mb: Int) {
        maxGainMb = mb.coerceIn(0, GainMapper.MAX_GAIN_MB).let { capped ->
            if (capped <= 0) GainMapper.MAX_GAIN_MB else capped
        }
    }

    fun getOrCreate(sessionId: Int): SessionEffects {
        return sessions.getOrPut(sessionId) {
            SessionEffects(
                loudnessEnhancer = factory.createLoudnessEnhancer(sessionId),
                dynamicsProcessing = factory.createDynamicsProcessing(sessionId),
            )
        }
    }

    fun applyGain(sessionId: Int, gainMb: Int, enable: Boolean): ApplyResult {
        val effects = getOrCreate(sessionId)
        return GainDiscoveryLogic.applyGain(
            enhancer = effects.loudnessEnhancer,
            gainMb = gainMb,
            maxGainMb = maxGainMb,
            enable = enable,
        )
    }

    fun currentMaxGainMb(): Int = maxGainMb

    fun applyDynamicsGain(sessionId: Int, gainDb: Float, enable: Boolean): ApplyResult {
        val effects = getOrCreate(sessionId)
        val dp = effects.dynamicsProcessing
            ?: return ApplyResult(success = true, effectiveGainMb = 0, statusCode = 0)
        dp.enabled = enable
        if (!enable) {
            return ApplyResult(success = true, effectiveGainMb = 0, statusCode = 0)
        }
        val clampedDb = gainDb.coerceIn(0f, GainMapper.MAX_DYNAMICS_DB)
        val status = dp.setPostGain(clampedDb)
        return ApplyResult(
            success = status == 0,
            effectiveGainMb = (clampedDb * 150).toInt(),
            statusCode = status,
        )
    }

    fun releaseAndRecreate(sessionId: Int): SessionEffects {
        sessions.remove(sessionId)?.release()
        return getOrCreate(sessionId)
    }

    fun releaseAll() {
        sessions.values.forEach { it.release() }
        sessions.clear()
    }

    fun sessionCount(): Int = sessions.size

    fun isGlobalEnabled(): Boolean =
        sessions[GLOBAL_SESSION]?.loudnessEnhancer?.enabled == true

    fun getGlobalTargetGainMb(): Int {
        val effects = sessions[GLOBAL_SESSION] ?: return 0
        return if (effects.loudnessEnhancer.enabled) {
            effects.loudnessEnhancer.let { handle ->
                // Fake handles track gain; real handles verified via watchdog
                handle.lastAppliedGainMb
            }
        } else {
            0
        }
    }

    data class SessionEffects(
        val loudnessEnhancer: LoudnessEnhancerHandle,
        val dynamicsProcessing: DynamicsProcessingHandle?,
    ) {
        fun release() {
            loudnessEnhancer.release()
            dynamicsProcessing?.release()
        }
    }

    data class ApplyResult(
        val success: Boolean,
        val effectiveGainMb: Int,
        val statusCode: Int,
    )
}

val LoudnessEnhancerHandle.lastAppliedGainMb: Int
    get() = (this as? GainTrackingHandle)?.lastGainMb ?: 0

interface GainTrackingHandle {
    val lastGainMb: Int
}