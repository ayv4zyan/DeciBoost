package com.deciboost.core.audio.policy

object GainMapper {
    /** BoostX-comparable: slider 0–100 over-boost → 0–3000 mB (0–30 dB) at 150% UI */
    fun toTargetGainMilliBel(percent: BoostPercent): Int =
        ((percent.value - 100) * 30).coerceIn(0, MAX_GAIN_MB)

    fun toDynamicsGainDb(gainMb: Int): Float =
        (gainMb / 150f).coerceIn(0f, MAX_DYNAMICS_DB)

    const val MAX_GAIN_MB = 3000
    const val MAX_DYNAMICS_DB = 20f
}