package com.deciboost.core.audio.policy

import org.junit.Assert.assertEquals

data class PlaybackVectorResult(
    val reapplyCount: Int,
    val finalPhase: PlaybackPhase,
)

object PlaybackVectorRunner {

    fun run(vector: PlaybackVector): PlaybackVectorResult {
        var now = 0L
        var reapplyCount = 0
        var recreateCount = 0
        val guard = NonMediaPlaybackGuard(enabled = true)
        var activeConfigs = emptyList<ConfigSnapshot>()
        lateinit var tracker: PlaybackActivityTracker
        tracker = PlaybackActivityTracker(
            onReapply = {
                if (!guard.shouldSuspendBoost(activeConfigs, isMusicActive = tracker.isMusicActive())) {
                    reapplyCount++
                }
            },
            onReleaseAndRecreate = { recreateCount++ },
            nowMs = { now },
        )
        tracker.setBoostPercent(vector.boostPercent)

        vector.configCounts.zip(vector.musicActive).forEachIndexed { index, (count, active) ->
            val config = vector.configs?.getOrNull(index)
                ?: ConfigSnapshot(count = count, usageHash = count + index)
            activeConfigs = listOf(config)
            tracker.onConfigChanged(config)
            tracker.onMusicActiveChanged(active)
            if (!active) {
                now += PlaybackActivityTracker.PAUSE_HOLD_MS + 10
                tracker.onTick()
            }
            if (index > 0 && !vector.musicActive[index - 1] && active) {
                now += 50
                tracker.onTick()
                tracker.markReapplySuccess()
            }
            now += 50
            tracker.onTick()
        }

        if (vector.deviceEvent) {
            tracker.onDeviceChanged()
        }

        return PlaybackVectorResult(
            reapplyCount = reapplyCount,
            finalPhase = tracker.phase.value,
        )
    }

    fun assertVector(vector: PlaybackVector) {
        val result = run(vector)
        assertEquals(
            "Vector ${vector.id}: expected phase ${vector.expectedFinalPhase}",
            vector.expectedFinalPhase,
            result.finalPhase,
        )
        if (vector.expectedMinReapplies == 0) {
            assertEquals(
                "Vector ${vector.id}: expected zero reapplies",
                0,
                result.reapplyCount,
            )
        } else {
            org.junit.Assert.assertTrue(
                "Vector ${vector.id}: expected at least ${vector.expectedMinReapplies} reapplies " +
                    "but got ${result.reapplyCount}",
                result.reapplyCount >= vector.expectedMinReapplies,
            )
        }
    }
}
