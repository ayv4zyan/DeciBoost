package com.deciboost.core.audio.policy

object GainDiscoveryLogic {

    fun applyGain(
        enhancer: LoudnessEnhancerHandle,
        gainMb: Int,
        maxGainMb: Int,
        enable: Boolean,
    ): SessionEffectRegistry.ApplyResult {
        enhancer.enabled = enable
        if (!enable) {
            return SessionEffectRegistry.ApplyResult(
                success = true,
                effectiveGainMb = 0,
                statusCode = 0,
            )
        }
        var requested = gainMb.coerceIn(0, maxGainMb)
        var status = enhancer.setTargetGain(requested)
        while (status == SessionEffectRegistry.ERROR_BAD_VALUE && requested > 0) {
            requested -= 100
            status = enhancer.setTargetGain(requested)
        }
        val success = status == 0
        return SessionEffectRegistry.ApplyResult(
            success = success,
            effectiveGainMb = if (success) requested else 0,
            statusCode = status,
        )
    }
}
