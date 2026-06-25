package com.deciboost.app.debug

import com.deciboost.core.audio.policy.ReapplyReason

interface BoostEngineProbe {
    fun getGlobalTargetGainMilliBel(): Int
    fun isGlobalEffectEnabled(): Boolean
    fun getLastReapplyReason(): ReapplyReason
    fun measureRmsRatio(): Float
}