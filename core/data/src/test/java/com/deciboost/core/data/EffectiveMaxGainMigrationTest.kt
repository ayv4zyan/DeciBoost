package com.deciboost.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveMaxGainMigrationTest {

    @Test
    fun resolve_prefersFingerprintKey() {
        val result = EffectiveMaxGainMigration.resolveEffectiveMaxGainMb(
            fingerprintKey = "effective_max_gain_mb_abc123",
            entries = mapOf(
                "effective_max_gain_mb_abc123" to 2500,
                EffectiveMaxGainMigration.LEGACY_KEY to 3000,
            ),
        )
        assertEquals(2500, result)
    }

    @Test
    fun resolve_fallsBackToLegacyKey() {
        val result = EffectiveMaxGainMigration.resolveEffectiveMaxGainMb(
            fingerprintKey = "effective_max_gain_mb_newdevice",
            entries = mapOf(EffectiveMaxGainMigration.LEGACY_KEY to 2200),
        )
        assertEquals(2200, result)
    }

    @Test
    fun resolve_fallsBackToOrphanedFingerprintKey() {
        val result = EffectiveMaxGainMigration.resolveEffectiveMaxGainMb(
            fingerprintKey = "effective_max_gain_mb_newdevice",
            entries = mapOf(
                "effective_max_gain_mb_olddevice" to 1800,
                "effective_max_gain_mb_other" to 2100,
            ),
        )
        assertEquals(2100, result)
    }

    @Test
    fun migrationSource_returnsNullWhenFingerprintKeyExists() {
        val source = EffectiveMaxGainMigration.migrationSourceValue(
            fingerprintKey = "effective_max_gain_mb_abc",
            entries = mapOf("effective_max_gain_mb_abc" to 1500),
        )
        assertNull(source)
    }

    @Test
    fun migrationSource_prefersLegacyOverOrphaned() {
        val source = EffectiveMaxGainMigration.migrationSourceValue(
            fingerprintKey = "effective_max_gain_mb_new",
            entries = mapOf(
                EffectiveMaxGainMigration.LEGACY_KEY to 2400,
                "effective_max_gain_mb_old" to 1800,
            ),
        )
        assertEquals(2400, source)
    }
}