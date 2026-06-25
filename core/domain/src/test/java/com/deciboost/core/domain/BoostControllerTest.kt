package com.deciboost.core.domain

import com.deciboost.core.audio.policy.BoostPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoostControllerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun killSwitch_resetsBoostTo100AndStopsService() = runTest(testDispatcher) {
        val engine = FakeBoostEngine()
        val preferences = FakeBoostPreferences()
        val coordinator = FakeBoostServiceCoordinator()
        preferences.setBoostPercent(175)
        engine.setBoost(BoostPercent(175))

        val controller = createController(engine, preferences, coordinator)
        advanceUntilIdle()

        preferences.setKillSwitchEnabled(true)
        advanceUntilIdle()

        assertEquals(100, preferences.currentBoostPercent)
        assertTrue(engine.appliedBoosts.contains(100))
        assertEquals(100, controller.state.value.boostPercent.value)
        assertEquals(1, coordinator.stopCount)
    }

    @Test
    fun killSwitch_blocksSetBoostPercent() = runTest(testDispatcher) {
        val engine = FakeBoostEngine()
        val preferences = FakeBoostPreferences()
        preferences.setKillSwitchEnabled(true)
        preferences.setBoostPercent(100)

        val controller = createController(engine, preferences)
        advanceUntilIdle()

        controller.setBoostPercent(150)
        advanceUntilIdle()

        assertEquals(100, preferences.currentBoostPercent)
        assertFalse(engine.appliedBoosts.contains(150))
    }

    @Test
    fun gradualBoost_appliesMonotonicFivePercentSteps() = runTest(testDispatcher) {
        val engine = FakeBoostEngine()
        val preferences = FakeBoostPreferences()
        preferences.setGradualBoost(true)
        preferences.setBoostPercent(100)

        val controller = createController(engine, preferences)
        advanceUntilIdle()

        controller.setBoostPercent(120)
        advanceUntilIdle()

        assertEquals(listOf(105, 110, 115, 120), engine.appliedBoosts)
        assertEquals(120, preferences.currentBoostPercent)
        assertEquals(120, controller.state.value.boostPercent.value)
    }

    @Test
    fun killSwitchDuringRamp_cancelsRampAndResetsTo100() = runTest(testDispatcher) {
        val engine = FakeBoostEngine()
        val preferences = FakeBoostPreferences()
        val coordinator = FakeBoostServiceCoordinator()
        preferences.setGradualBoost(true)
        preferences.setBoostPercent(100)

        val controller = createController(engine, preferences, coordinator)
        advanceUntilIdle()

        val ramp = async { controller.setBoostPercent(200) }
        advanceTimeBy(BoostController.GRADUAL_RAMP_DELAY_MS)
        preferences.setKillSwitchEnabled(true)
        advanceUntilIdle()
        ramp.await()

        assertEquals(100, preferences.currentBoostPercent)
        assertTrue(engine.appliedBoosts.contains(100))
        assertFalse(engine.appliedBoosts.last() > 100)
        assertEquals(1, coordinator.stopCount)
    }

    @Test
    fun asyncEngine_updatesControllerStateViaListenerOnly() = runTest(testDispatcher) {
        val engine = AsyncFakeBoostEngine(this)
        val preferences = FakeBoostPreferences()
        preferences.setGradualBoost(false)
        preferences.setBoostPercent(100)

        val controller = createController(engine, preferences)
        advanceUntilIdle()

        val setBoostJob = async { controller.setBoostPercent(120) }
        runCurrent()
        assertEquals(100, controller.state.value.boostPercent.value)

        advanceUntilIdle()
        setBoostJob.await()

        assertEquals(120, controller.state.value.boostPercent.value)
        assertEquals(listOf(120), engine.appliedBoosts)
    }

    @Test
    fun concurrentSetBoostPercent_serializesToLastTarget() = runTest(testDispatcher) {
        val engine = FakeBoostEngine()
        val preferences = FakeBoostPreferences()
        preferences.setGradualBoost(true)
        preferences.setBoostPercent(100)

        val controller = createController(engine, preferences)
        advanceUntilIdle()

        val first = async { controller.setBoostPercent(130) }
        advanceTimeBy(1)
        val second = async { controller.setBoostPercent(120) }
        advanceUntilIdle()
        runCatching { first.await() }
        second.await()

        assertEquals(120, preferences.currentBoostPercent)
        assertEquals(120, engine.appliedBoosts.last())
        assertTrue(engine.appliedBoosts.zipWithNext().all { (a, b) -> a <= b })
    }
}