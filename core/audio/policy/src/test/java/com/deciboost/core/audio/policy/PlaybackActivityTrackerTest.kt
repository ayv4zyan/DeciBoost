package com.deciboost.core.audio.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        recreateCount = 0
        tracker.onDeviceChanged()
        assertEquals(1, recreateCount)
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
    fun `notification dominant with music active does not block tracker reapplies`() {
        val guard = NonMediaPlaybackGuard(enabled = true)
        val configs = listOf(
            ConfigSnapshot(1, 5, hasMediaUsage = false, hasNotificationUsage = true),
            ConfigSnapshot(1, 5, hasMediaUsage = false, hasNotificationUsage = true),
        )
        var effectiveReapplies = 0
        var activeConfigs = emptyList<ConfigSnapshot>()
        lateinit var notificationTracker: PlaybackActivityTracker
        notificationTracker = PlaybackActivityTracker(
            onReapply = {
                if (!guard.shouldSuspendBoost(activeConfigs, isMusicActive = notificationTracker.isMusicActive())) {
                    effectiveReapplies++
                }
            },
            onReleaseAndRecreate = { recreateCount++ },
            nowMs = { now },
        )
        notificationTracker.setBoostPercent(150)
        configs.forEach { config ->
            activeConfigs = listOf(config)
            assertFalse(guard.shouldSuspendBoost(activeConfigs, isMusicActive = true))
            notificationTracker.onConfigChanged(config)
            notificationTracker.onMusicActiveChanged(true)
            now += 50
            notificationTracker.onTick()
        }
        assertTrue(effectiveReapplies >= 1)
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
    fun `trailing-edge debounce delivers latest snapshot after burst`() {
        tracker.onMusicActiveChanged(true)
        reapplyCount = 0
        tracker.onConfigChanged(ConfigSnapshot(1, 1))
        now += 30
        tracker.onConfigChanged(ConfigSnapshot(2, 2))
        now += 30
        tracker.onConfigChanged(ConfigSnapshot(1, 3))
        now += 30
        tracker.onTick()
        assertEquals(0, reapplyCount)

        now += PlaybackActivityTracker.CONFIG_DEBOUNCE_MS
        tracker.onTick()
        assertEquals(1, reapplyCount)
        assertEquals(ConfigSnapshot(1, 3), tracker.lastConfigForTest())
    }

    @Test
    fun `debounce deadline extends while burst continues`() {
        tracker.onMusicActiveChanged(true)
        reapplyCount = 0
        tracker.onConfigChanged(ConfigSnapshot(1, 1))
        now += 50
        tracker.onConfigChanged(ConfigSnapshot(2, 2))
        now += 80
        tracker.onTick()
        assertEquals(0, reapplyCount)

        now += 30
        tracker.onTick()
        assertEquals(1, reapplyCount)
        assertEquals(ConfigSnapshot(2, 2), tracker.lastConfigForTest())
    }

    @Test
    fun `stale frame during debounce does not cancel pending snapshot`() {
        tracker.onMusicActiveChanged(true)
        tracker.onConfigChanged(ConfigSnapshot(2, 1))
        now += PlaybackActivityTracker.CONFIG_DEBOUNCE_MS
        tracker.onTick()
        assertEquals(ConfigSnapshot(2, 1), tracker.lastConfigForTest())

        reapplyCount = 0
        tracker.onConfigChanged(ConfigSnapshot(1, 3))
        now += 30
        tracker.onConfigChanged(ConfigSnapshot(2, 1))
        now += PlaybackActivityTracker.CONFIG_DEBOUNCE_MS
        tracker.onTick()

        assertEquals(1, reapplyCount)
        assertEquals(ConfigSnapshot(1, 3), tracker.lastConfigForTest())
    }

    @Test
    fun `config reapply suppressed when phase is not active`() {
        tracker.onMusicActiveChanged(true)
        reapplyCount = 0
        tracker.onMusicActiveChanged(false)
        now += PlaybackActivityTracker.PAUSE_HOLD_MS + 10
        tracker.onTick()
        assertEquals(PlaybackPhase.Paused, tracker.phase.value)

        tracker.onConfigChanged(ConfigSnapshot(2, 5))
        now += PlaybackActivityTracker.CONFIG_DEBOUNCE_MS
        tracker.onTick()
        assertEquals(0, reapplyCount)
    }

    @Test
    fun `periodic recreate fires after active stale interval`() {
        tracker.onMusicActiveChanged(true)
        tracker.onConfigChanged(ConfigSnapshot(1, 1))
        now += PlaybackActivityTracker.CONFIG_DEBOUNCE_MS
        tracker.onTick()

        val reappliesBeforePeriodic = reapplyCount
        val recreatesBeforePeriodic = recreateCount
        now += PlaybackActivityTracker.ACTIVE_STALE_RECREATE_MS
        tracker.onTick()

        assertEquals(recreatesBeforePeriodic, recreateCount)
        assertEquals(reappliesBeforePeriodic + 1, reapplyCount)
    }

    @Test
    fun `recovering exhaustion triggers release and recreate`() {
        tracker.onMusicActiveChanged(true)
        tracker.onConfigChanged(ConfigSnapshot(1, 1))
        tracker.onMusicActiveChanged(false)
        now += PlaybackActivityTracker.PAUSE_HOLD_MS + 10
        tracker.onTick()
        assertEquals(PlaybackPhase.Paused, tracker.phase.value)

        recreateCount = 0
        tracker.onMusicActiveChanged(true)
        assertEquals(PlaybackPhase.Recovering, tracker.phase.value)

        repeat(PlaybackActivityTracker.MAX_RECOVERING_RETRIES) {
            now += PlaybackActivityTracker.RECOVERING_TIMEOUT_MS + 1
            tracker.onTick()
        }
        assertEquals(PlaybackPhase.Paused, tracker.phase.value)
        assertEquals(1, recreateCount)
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
