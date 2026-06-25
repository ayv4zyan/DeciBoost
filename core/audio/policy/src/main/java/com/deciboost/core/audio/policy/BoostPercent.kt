package com.deciboost.core.audio.policy

/** User-facing boost: 100 = system max (no enhancement), 200 = max UI boost */
@JvmInline
value class BoostPercent(val value: Int) {
    init {
        require(value in 100..FeatureFlags.MAX_BOOST_CAP) {
            "Boost must be between 100 and ${FeatureFlags.MAX_BOOST_CAP}"
        }
    }
}
