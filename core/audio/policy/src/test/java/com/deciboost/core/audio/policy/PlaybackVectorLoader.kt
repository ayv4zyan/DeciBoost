package com.deciboost.core.audio.policy

import org.json.JSONObject
import java.io.File

data class PlaybackVector(
    val id: String,
    val configCounts: List<Int>,
    val musicActive: List<Boolean>,
    val boostPercent: Int = 150,
    val deviceEvent: Boolean = false,
    val configs: List<ConfigSnapshot>? = null,
    val expectedMinReapplies: Int,
    val expectedFinalPhase: PlaybackPhase,
)

object PlaybackVectorLoader {

    fun loadVectors(vararg ids: String): List<PlaybackVector> {
        val file = sequenceOf(
            File("testing/vectors/playback_sequences.json"),
            File("../../testing/vectors/playback_sequences.json"),
            File("../../../testing/vectors/playback_sequences.json"),
        ).firstOrNull { it.exists() }
            ?: error("playback_sequences.json not found")

        val root = JSONObject(file.readText())
        val vectors = root.getJSONArray("vectors")
        val wanted = ids.toSet()
        val loaded = mutableListOf<PlaybackVector>()
        for (index in 0 until vectors.length()) {
            val entry = vectors.getJSONObject(index)
            val id = entry.getString("id")
            if (id !in wanted) continue
            loaded.add(parseVector(entry))
        }
        require(loaded.size == ids.size) {
            "Expected vectors $ids but loaded ${loaded.map { it.id }}"
        }
        return loaded
    }

    private fun parseVector(entry: JSONObject): PlaybackVector {
        val configCounts = entry.getJSONArray("config_counts").let { array ->
            List(array.length()) { array.getInt(it) }
        }
        val musicActive = entry.getJSONArray("music_active").let { array ->
            List(array.length()) { array.getBoolean(it) }
        }
        val configs = entry.optJSONArray("configs")?.let { array ->
            List(array.length()) { index ->
                val config = array.getJSONObject(index)
                ConfigSnapshot(
                    count = config.getInt("count"),
                    usageHash = config.getInt("usageHash"),
                    hasMediaUsage = config.getBoolean("hasMediaUsage"),
                    hasNotificationUsage = config.getBoolean("hasNotificationUsage"),
                )
            }
        }
        return PlaybackVector(
            id = entry.getString("id"),
            configCounts = configCounts,
            musicActive = musicActive,
            boostPercent = entry.optInt("boost_percent", 150),
            deviceEvent = entry.optBoolean("device_event", false),
            configs = configs,
            expectedMinReapplies = entry.getInt("expected_min_reapplies"),
            expectedFinalPhase = PlaybackPhase.valueOf(entry.getString("expected_final_phase")),
        )
    }
}
