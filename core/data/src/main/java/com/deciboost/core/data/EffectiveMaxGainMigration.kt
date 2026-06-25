package com.deciboost.core.data

/**
 * Pure migration logic for fingerprint-scoped effective max gain keys.
 */
object EffectiveMaxGainMigration {

    const val LEGACY_KEY = "effective_max_gain_mb"

    fun resolveEffectiveMaxGainMb(
        fingerprintKey: String,
        entries: Map<String, Int>,
        defaultMb: Int = BoostPreferencesDataStore.DEFAULT_EFFECTIVE_MAX_GAIN_MB,
    ): Int {
        entries[fingerprintKey]?.let { return it }
        entries[LEGACY_KEY]?.let { return it }
        return entries.entries
            .filter { (key, _) -> DeviceFingerprint.isEffectiveMaxGainKey(key) && key != fingerprintKey }
            .maxOfOrNull { it.value }
            ?: defaultMb
    }

    fun migrationSourceValue(
        fingerprintKey: String,
        entries: Map<String, Int>,
    ): Int? {
        if (entries.containsKey(fingerprintKey)) return null
        entries[LEGACY_KEY]?.let { return it }
        return entries.entries
            .filter { (key, _) -> DeviceFingerprint.isEffectiveMaxGainKey(key) && key != fingerprintKey }
            .maxOfOrNull { it.value }
    }
}