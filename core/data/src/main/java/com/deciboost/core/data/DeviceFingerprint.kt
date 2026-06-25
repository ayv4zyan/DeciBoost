package com.deciboost.core.data

import android.os.Build
import java.security.MessageDigest

/**
 * Stable per-device identifier for scoping learned gain limits (DESIGN.md).
 * Uses [Build.FINGERPRINT] hashed to keep DataStore keys short.
 */
object DeviceFingerprint {

    private const val HASH_PREFIX_BYTES = 8
    private const val EFFECTIVE_MAX_GAIN_PREFIX = "effective_max_gain_mb_"

    fun hashFingerprint(rawFingerprint: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(rawFingerprint.toByteArray(Charsets.UTF_8))
        return bytes.take(HASH_PREFIX_BYTES).joinToString("") { "%02x".format(it) }
    }

    fun hash(): String = hashFingerprint(Build.FINGERPRINT)

    fun effectiveMaxGainMbKeyFor(fingerprintHash: String): String =
        "$EFFECTIVE_MAX_GAIN_PREFIX$fingerprintHash"

    fun effectiveMaxGainMbKey(): String = effectiveMaxGainMbKeyFor(hash())

    fun isEffectiveMaxGainKey(keyName: String): Boolean = keyName.startsWith(EFFECTIVE_MAX_GAIN_PREFIX)
}
