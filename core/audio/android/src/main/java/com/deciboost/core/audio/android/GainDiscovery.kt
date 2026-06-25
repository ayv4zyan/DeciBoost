package com.deciboost.core.audio.android

import com.deciboost.core.audio.policy.GainDiscoveryLogic
import com.deciboost.core.audio.policy.LoudnessEnhancerHandle
import com.deciboost.core.audio.policy.SessionEffectRegistry
/**
 * Runtime gain clamping per DESIGN.md — discovers the highest accepted LoudnessEnhancer
 * target gain when the platform returns [SessionEffectRegistry.ERROR_BAD_VALUE].
 *
 * Learned caps are persisted per device fingerprint via
 * [DeviceFingerprint.effectiveMaxGainMbKey] in [com.deciboost.core.data.BoostPreferencesDataStore].
 */
object GainDiscovery {

    /** Default cap (mB) before per-device discovery; matches DESIGN.md. */
    const val DEFAULT_MAX_GAIN_MB = 3000

    fun applyGain(
        enhancer: LoudnessEnhancerHandle,
        gainMb: Int,
        maxGainMb: Int,
        enable: Boolean,
    ): SessionEffectRegistry.ApplyResult = GainDiscoveryLogic.applyGain(
        enhancer = enhancer,
        gainMb = gainMb,
        maxGainMb = maxGainMb,
        enable = enable,
    )
}
