package com.deciboost.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFingerprintTest {

    @Test
    fun hashFingerprint_isStableForSameInput() {
        val first = DeviceFingerprint.hashFingerprint("google/pixel/build-1")
        val second = DeviceFingerprint.hashFingerprint("google/pixel/build-1")
        assertEquals(first, second)
        assertEquals(16, first.length)
    }

    @Test
    fun hashFingerprint_differsForDifferentInput() {
        val a = DeviceFingerprint.hashFingerprint("google/pixel/build-1")
        val b = DeviceFingerprint.hashFingerprint("google/pixel/build-2")
        assertNotEquals(a, b)
    }

    @Test
    fun effectiveMaxGainMbKey_usesFingerprintHash() {
        val key = DeviceFingerprint.effectiveMaxGainMbKeyFor("abcd1234")
        assertEquals("effective_max_gain_mb_abcd1234", key)
        assertTrue(DeviceFingerprint.isEffectiveMaxGainKey(key))
    }
}
