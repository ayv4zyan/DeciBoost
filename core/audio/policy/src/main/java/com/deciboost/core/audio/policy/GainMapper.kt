package com.deciboost.core.audio.policy

object GainMapper {
    /** BoostX-comparable: slider 0–100 over-boost → 0–3000 mB (0–30 dB) at 150% UI */
    fun toTargetGainMilliBel(percent: BoostPercent): Int =
        ((percent.value - FeatureFlags.MIN_BOOST_PERCENT) * MILLIBEL_PER_PERCENT)
            .coerceIn(0, MAX_GAIN_MB)

    fun toDynamicsGainDb(gainMb: Int): Float =
        (gainMb / DYNAMICS_DB_DIVISOR).coerceIn(0f, MAX_DYNAMICS_DB)

    const val MILLIBEL_PER_PERCENT = 30
    const val DYNAMICS_DB_DIVISOR = 150f
    const val MAX_GAIN_MB = 3000
    const val MAX_DYNAMICS_DB = 20f
}
