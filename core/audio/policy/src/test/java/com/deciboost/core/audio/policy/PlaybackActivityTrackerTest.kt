package com.deciboost.core.audio.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PlaybackActivityTrackerTest {

    private var now = 0L
    private var reapplyCount = 0
    private var recreateCount = 0
    private lateinit var tracker: PlaybackActivityTracker

    @Before
    fun setUp() {
        now = 0L
        reapplyCount = 0
        recreateCount = 0
        tracker = PlaybackActivityTracker(
            onReapply = { reapplyCount++ },
            onReleaseAndRecreate = { recreateCount++ },
            nowMs = { now },
        )
        tracker.setBoostPercent(150)
    }

    @Test
    fun `pause resume basic vector`() {
        runVector(
            configCounts = listOf(2, 2, 2, 2),
            musicActive = listOf(true, true, false, true),
            minReapplies = 2,
        )
    }

    @Test
    fun `youtube like churn vector`() {
        runVector(
            configCounts = listOf(1, 2, 1, 2),
            musicActive = listOf(true, true, false, true),
            minReapplies = 3,
        )
    }

    @Test
    fun `bluetooth route triggers recreate`() {
        tracker.onMusicActiveChanged(true)
        tracker.onConfigChanged(ConfigSnapshot(2, 1))
        tracker.onDeviceChanged()
        assertTrue(recreateCount >= 1)
        assertTrue(reapplyCount >= 1)
    }

    @Test
    fun `idle no boost emits zero reapplies`() {
        val idleTracker = PlaybackActivityTracker(
            onReapply = { reapplyCount++ },
            onReleaseAndRecreate = { recreateCount++ },
            nowMs = { now },
        )
        idleTracker.setBoostPercent(100)
        idleTracker.onConfigChanged(ConfigSnapshot(0, 0))
        idleTracker.onMusicActiveChanged(false)
        now += 100
        idleTracker.onTick()
        assertEquals(0, idleTracker.reapplyCount())
    }

    @Test
    fun `notification dominant vector at 150 guard blocks tracker reapplies`() {
        val guard = NonMediaPlaybackGuard(enabled = true)
        val configs = listOf(
            ConfigSnapshot(1, 5, hasMediaUsage = false, hasNotificationUsage = true),
            ConfigSnapshot(1, 5, hasMediaUsage = false, hasNotificationUsage = true),
        )
        var effectiveReapplies = 0
        var activeConfigs = emptyList<ConfigSnapshot>()
        val notificationTracker = PlaybackActivityTracker(
            onReapply = {
                if (!guard.shouldSuspendBoost(activeConfigs)) {
                    effectiveReapplies++
                }
            },
            onReleaseAndRecreate = { recreateCount++ },
            nowMs = { now },
        )
        notificationTracker.setBoostPercent(150)
        configs.forEach { config ->
            activeConfigs = listOf(config)
            assertTrue(guard.shouldSuspendBoost(activeConfigs))
            notificationTracker.onConfigChanged(config)
            notificationTracker.onMusicActiveChanged(true)
            now += 50
            notificationTracker.onTick()
        }
        assertEquals(0, effectiveReapplies)
    }

    @Test
    fun `notification dominant vector guard suspends and tracker idle at boost 100`() {
        val guard = NonMediaPlaybackGuard(enabled = true)
        val configs = listOf(
            ConfigSnapshot(1, 5, hasMediaUsage = false, hasNotificationUsage = true),
        )
        assertTrue(guard.shouldSuspendBoost(configs))
        assertEquals(NonMediaPlaybackGuard.GuardAction.Suspend, guard.evaluate(configs))
        reapplyCount = 0
        val notificationTracker = PlaybackActivityTracker(
            onReapply = { reapplyCount++ },
            onReleaseAndRecreate = { recreateCount++ },
            nowMs = { now },
        )
        notificationTracker.setBoostPercent(100)
        notificationTracker.onConfigChanged(configs.first())
        notificationTracker.onMusicActiveChanged(true)
        now += 100
        notificationTracker.onTick()
        assertEquals(0, notificationTracker.reapplyCount())
    }

    @Test
    fun `idle shutdown fires once after five minutes at boost 100`() {
        var shutdownCount = 0
        val idleTracker = PlaybackActivityTracker(
            onReapply = { },
            onReleaseAndRecreate = { },
            onIdleShutdown = { shutdownCount++ },
            nowMs = { now },
        )
        idleTracker.setBoostPercent(100)
        now += PlaybackActivityTracker.IDLE_TIMEOUT_MS
        idleTracker.onTick()
        assertEquals(1, shutdownCount)
        idleTracker.onTick()
        assertEquals(1, shutdownCount)
    }

    @Test
    fun `boost above 100 cancels idle shutdown timer`() {
        var shutdownCount = 0
        val idleTracker = PlaybackActivityTracker(
            onReapply = { },
            onReleaseAndRecreate = { },
            onIdleShutdown = { shutdownCount++ },
            nowMs = { now },
        )
        idleTracker.setBoostPercent(100)
        now += PlaybackActivityTracker.IDLE_TIMEOUT_MS / 2
        idleTracker.setBoostPercent(150)
        now += PlaybackActivityTracker.IDLE_TIMEOUT_MS
        idleTracker.onTick()
        assertEquals(0, shutdownCount)
    }

    @Test
    fun `engine restart re-arms idle shutdown after prior fire`() {
        var shutdownCount = 0
        val idleTracker = PlaybackActivityTracker(
            onReapply = { },
            onReleaseAndRecreate = { },
            onIdleShutdown = { shutdownCount++ },
            nowMs = { now },
        )
        idleTracker.setBoostPercent(100)
        now += PlaybackActivityTracker.IDLE_TIMEOUT_MS
        idleTracker.onTick()
        assertEquals(1, shutdownCount)

        idleTracker.onEngineStarted()
        now += PlaybackActivityTracker.IDLE_TIMEOUT_MS
        idleTracker.onTick()
        assertEquals(2, shutdownCount)
    }

    @Test
    fun `idle_no_boost vector from JSON`() {
        val vector = PlaybackVectorLoader.loadVectors("idle_no_boost").single()
        PlaybackVectorRunner.assertVector(vector)
    }

    @Test
    fun `notification_dominant vector from JSON`() {
        val vector = PlaybackVectorLoader.loadVectors("notification_dominant").single()
        PlaybackVectorRunner.assertVector(vector)
    }

    @Test
    fun `vectors file contains all expected ids`() {
        val vectorsFile = sequenceOf(
            File("testing/vectors/playback_sequences.json"),
            File("../../testing/vectors/playback_sequences.json"),
            File("../../../testing/vectors/playback_sequences.json"),
        ).firstOrNull { it.exists() }
            ?: error("playback_sequences.json not found")
        val content = vectorsFile.readText()
        listOf(
            "pause_resume_basic",
            "youtube_like_churn",
            "bluetooth_route",
            "idle_no_boost",
            "notification_dominant",
        ).forEach { id ->
            assertTrue(content.contains(id))
        }
    }

    private fun runVector(
        configCounts: List<Int>,
        musicActive: List<Boolean>,
        minReapplies: Int,
    ) {
        tracker.resetReapplyCount()
        reapplyCount = 0
        configCounts.zip(musicActive).forEachIndexed { index, (count, active) ->
            tracker.onConfigChanged(ConfigSnapshot(count, count + index))
            tracker.onMusicActiveChanged(active)
            if (!active) {
                now += PlaybackActivityTracker.PAUSE_HOLD_MS + 10
                tracker.onTick()
            }
            if (index > 0 && !musicActive[index - 1] && active) {
                now += 50
                tracker.onTick()
                tracker.markReapplySuccess()
            }
            now += 50
            tracker.onTick()
        }
        assertTrue(
            "Expected at least $minReapplies reapplies but got $reapplyCount",
            reapplyCount >= minReapplies,
        )
    }
}
