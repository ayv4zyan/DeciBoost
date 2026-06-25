package com.deciboost.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class BoostPreferencesDataStoreMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun isolatedStore(suffix: String): BoostPreferencesDataStore =
        BoostPreferencesDataStore(context, "boost_preferences_test_$suffix")

    @Test
    fun migrateEffectiveMaxGainMbIfNeeded_copiesLegacyKeyToFingerprintKey() = runBlocking {
        val store = isolatedStore("legacy")
        store.seedLegacyEffectiveMaxGainMbForTest(2200)
        assertNull(store.readFingerprintEffectiveMaxGainMbForTest())

        store.migrateEffectiveMaxGainMbIfNeeded()

        assertEquals(2200, store.readFingerprintEffectiveMaxGainMbForTest())
        assertEquals(2200, store.effectiveMaxGainMb.first())
    }

    @Test
    fun migrateEffectiveMaxGainMbIfNeeded_copiesHighestOrphanedFingerprintKey() = runBlocking {
        val store = isolatedStore("orphaned")
        store.seedOrphanedFingerprintGainForTest("olddevice1", 1800)
        store.seedOrphanedFingerprintGainForTest("olddevice2", 2100)
        assertNull(store.readFingerprintEffectiveMaxGainMbForTest())

        store.migrateEffectiveMaxGainMbIfNeeded()

        assertEquals(2100, store.readFingerprintEffectiveMaxGainMbForTest())
        assertEquals(2100, store.effectiveMaxGainMb.first())
    }

    @Test
    fun migrateEffectiveMaxGainMbIfNeeded_isNoOpWhenFingerprintKeyExists() = runBlocking {
        val store = isolatedStore("noop")
        store.setEffectiveMaxGainMb(2500)
        store.seedLegacyEffectiveMaxGainMbForTest(999)

        store.migrateEffectiveMaxGainMbIfNeeded()

        assertEquals(2500, store.readFingerprintEffectiveMaxGainMbForTest())
        assertEquals(2500, store.effectiveMaxGainMb.first())
    }
}
