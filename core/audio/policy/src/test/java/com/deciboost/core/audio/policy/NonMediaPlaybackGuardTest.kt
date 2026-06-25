package com.deciboost.core.audio.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NonMediaPlaybackGuardTest {

    @Test
    fun `suspends when only notification usage active`() {
        val guard = NonMediaPlaybackGuard()
        val configs = listOf(
            ConfigSnapshot(1, 1, hasMediaUsage = false, hasNotificationUsage = true),
        )
        assertEquals(NonMediaPlaybackGuard.GuardAction.Suspend, guard.evaluate(configs))
    }

    @Test
    fun `does not suspend when media present`() {
        val guard = NonMediaPlaybackGuard()
        val configs = listOf(
            ConfigSnapshot(2, 2, hasMediaUsage = true, hasNotificationUsage = true),
        )
        assertFalse(guard.shouldSuspendBoost(configs))
    }

    @Test
    fun `does not suspend when music is active despite non-media configs`() {
        val guard = NonMediaPlaybackGuard()
        val configs = listOf(
            ConfigSnapshot(1, 1, hasMediaUsage = false, hasNotificationUsage = true),
        )
        assertFalse(guard.shouldSuspendBoost(configs, isMusicActive = true))
        assertEquals(NonMediaPlaybackGuard.GuardAction.None, guard.evaluate(configs, isMusicActive = true))
    }

    @Test
    fun `resumes when media returns`() {
        val guard = NonMediaPlaybackGuard()
        guard.evaluate(listOf(ConfigSnapshot(1, 1, hasMediaUsage = false, hasNotificationUsage = true)))
        val action = guard.evaluate(listOf(ConfigSnapshot(1, 2, hasMediaUsage = true)))
        assertEquals(NonMediaPlaybackGuard.GuardAction.Resume, action)
        assertTrue(!guard.isSuspended())
    }
}
