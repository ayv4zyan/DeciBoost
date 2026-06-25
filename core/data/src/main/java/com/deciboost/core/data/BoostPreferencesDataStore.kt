package com.deciboost.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.deciboost.core.audio.policy.FeatureFlags
import com.deciboost.core.data.DeviceFingerprint.effectiveMaxGainMbKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

private val Context.defaultBoostDataStore: DataStore<Preferences> by preferencesDataStore(
    BoostPreferencesDataStore.DEFAULT_DATASTORE_NAME,
)

private val namedBoostDataStores = ConcurrentHashMap<String, DataStore<Preferences>>()

private fun Context.boostDataStore(name: String): DataStore<Preferences> {
    if (name == BoostPreferencesDataStore.DEFAULT_DATASTORE_NAME) {
        return defaultBoostDataStore
    }
    val cacheKey = "${applicationContext.packageName}:$name"
    return namedBoostDataStores.getOrPut(cacheKey) {
        PreferenceDataStoreFactory.create(
            produceFile = { applicationContext.preferencesDataStoreFile(name) },
        )
    }
}

class BoostPreferencesDataStore(
    private val context: Context,
    dataStoreName: String = DEFAULT_DATASTORE_NAME,
) : BoostPreferences {

    private val dataStore = context.boostDataStore(dataStoreName)

    companion object {
        const val DEFAULT_DATASTORE_NAME = "boost_preferences"
        const val DEFAULT_EFFECTIVE_MAX_GAIN_MB = 3000
    }

    override val boostPercent: Flow<Int> = dataStore.data.map {
        it[Keys.BOOST_PERCENT] ?: 100
    }

    override val volumePercent: Flow<Int> = dataStore.data.map {
        it[Keys.VOLUME_PERCENT] ?: 100
    }

    override val autoStartOnBoot: Flow<Boolean> = dataStore.data.map {
        it[Keys.AUTO_START_BOOT] ?: false
    }

    override val gradualBoost: Flow<Boolean> = dataStore.data.map {
        it[Keys.GRADUAL_BOOST] ?: false
    }

    override val pauseOnNonMedia: Flow<Boolean> = dataStore.data.map {
        it[Keys.PAUSE_ON_NON_MEDIA] ?: true
    }

    override val killSwitchEnabled: Flow<Boolean> = dataStore.data.map {
        it[Keys.KILL_SWITCH_ENABLED] ?: false
    }

    override val onboardingComplete: Flow<Boolean> = dataStore.data.map {
        it[Keys.ONBOARDING_COMPLETE] ?: false
    }

    override val effectiveMaxGainMb: Flow<Int> = dataStore.data.map { prefs ->
        readEffectiveMaxGainMb(prefs)
    }

    override val safetyAcknowledgedLevels: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[Keys.SAFETY_ACKNOWLEDGED_LEVELS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    override val visualizerEnabled: Flow<Boolean> = dataStore.data.map {
        it[Keys.VISUALIZER_ENABLED] ?: false
    }

    override suspend fun setBoostPercent(value: Int) {
        dataStore.edit {
            it[Keys.BOOST_PERCENT] = value.coerceIn(
                FeatureFlags.MIN_BOOST_PERCENT,
                FeatureFlags.MAX_BOOST_CAP,
            )
        }
    }

    override suspend fun setVolumePercent(value: Int) {
        dataStore.edit { it[Keys.VOLUME_PERCENT] = value.coerceIn(0, 100) }
    }

    override suspend fun setAutoStartOnBoot(value: Boolean) {
        dataStore.edit { it[Keys.AUTO_START_BOOT] = value }
    }

    override suspend fun setGradualBoost(value: Boolean) {
        dataStore.edit { it[Keys.GRADUAL_BOOST] = value }
    }

    override suspend fun setPauseOnNonMedia(value: Boolean) {
        dataStore.edit { it[Keys.PAUSE_ON_NON_MEDIA] = value }
    }

    override suspend fun setKillSwitchEnabled(value: Boolean) {
        dataStore.edit { it[Keys.KILL_SWITCH_ENABLED] = value }
    }

    override suspend fun setOnboardingComplete(value: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = value }
    }

    override suspend fun setEffectiveMaxGainMb(value: Int) {
        val key = intPreferencesKey(effectiveMaxGainMbKey())
        dataStore.edit { it[key] = value.coerceIn(0, DEFAULT_EFFECTIVE_MAX_GAIN_MB) }
    }

    override suspend fun migrateEffectiveMaxGainMbIfNeeded() {
        dataStore.edit { prefs ->
            val fingerprintKey = intPreferencesKey(effectiveMaxGainMbKey())
            if (prefs.contains(fingerprintKey)) return@edit
            val entries = preferenceIntEntries(prefs)
            val source = EffectiveMaxGainMigration.migrationSourceValue(
                fingerprintKey = fingerprintKey.name,
                entries = entries,
            ) ?: return@edit
            prefs[fingerprintKey] = source
        }
    }

    internal suspend fun seedLegacyEffectiveMaxGainMbForTest(value: Int) {
        dataStore.edit { it[Keys.LEGACY_EFFECTIVE_MAX_GAIN_MB] = value }
    }

    internal suspend fun seedOrphanedFingerprintGainForTest(fingerprintHash: String, value: Int) {
        val key = intPreferencesKey(DeviceFingerprint.effectiveMaxGainMbKeyFor(fingerprintHash))
        dataStore.edit { it[key] = value }
    }

    internal suspend fun readFingerprintEffectiveMaxGainMbForTest(): Int? {
        val key = intPreferencesKey(effectiveMaxGainMbKey())
        return dataStore.data.map { it[key] }.first()
    }

    private fun readEffectiveMaxGainMb(prefs: Preferences): Int {
        val fingerprintKey = effectiveMaxGainMbKey()
        val entries = preferenceIntEntries(prefs)
        return EffectiveMaxGainMigration.resolveEffectiveMaxGainMb(
            fingerprintKey = fingerprintKey,
            entries = entries,
        )
    }

    override suspend fun setSafetyAcknowledgedLevels(levels: Set<Int>) {
        dataStore.edit {
            it[Keys.SAFETY_ACKNOWLEDGED_LEVELS] = levels.map { level -> level.toString() }.toSet()
        }
    }

    override suspend fun setVisualizerEnabled(value: Boolean) {
        dataStore.edit { it[Keys.VISUALIZER_ENABLED] = value }
    }

    private fun preferenceIntEntries(prefs: Preferences): Map<String, Int> =
        prefs.asMap().mapNotNull { (key, value) ->
            preferenceIntValue(value)?.let { key.name to it }
        }.toMap()

    private fun preferenceIntValue(value: Any?): Int? = when (value) {
        is Int -> value
        is Integer -> value.toInt()
        else -> null
    }

    private object Keys {
        val BOOST_PERCENT = intPreferencesKey("boost_percent")
        val VOLUME_PERCENT = intPreferencesKey("volume_percent")
        val GRADUAL_BOOST = booleanPreferencesKey("gradual_boost")
        val AUTO_START_BOOT = booleanPreferencesKey("auto_start_boot")
        val PAUSE_ON_NON_MEDIA = booleanPreferencesKey("pause_on_non_media")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KILL_SWITCH_ENABLED = booleanPreferencesKey("kill_switch_enabled")
        val LEGACY_EFFECTIVE_MAX_GAIN_MB = intPreferencesKey("effective_max_gain_mb")
        val SAFETY_ACKNOWLEDGED_LEVELS = stringSetPreferencesKey("safety_acknowledged_levels")
        val VISUALIZER_ENABLED = booleanPreferencesKey("visualizer_enabled")
    }
}
