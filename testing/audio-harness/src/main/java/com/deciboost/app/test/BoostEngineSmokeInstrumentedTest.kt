package com.deciboost.app.test

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fast smoke test: FGS starts, boost applies, probe responds with honest RMS checks.
 * Runs before the heavier pause/resume regression on CI emulators.
 */
@RunWith(AndroidJUnit4::class)
class BoostEngineSmokeInstrumentedTest {

    companion object {
        private const val DEBUG_PKG = "com.deciboost.app.debug"
    }

    @get:Rule
    val grantPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext

    @Before
    fun setUp() {
        EmulatorTestSupport.grantTestPermissions()
        EmulatorTestSupport.seedOnboardingComplete()
        RmsAssertionSupport.preflightRecordAudio()
    }

    @Test
    fun boostService_appliesGain_andProbeResponds() {
        EmulatorTestSupport.ensureTargetAppTonePlaying()
        instrumentation.context.startActivity(
            Intent(instrumentation.context, HarnessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        Thread.sleep(2000)

        val minRms = EmulatorTestSupport.minRmsThreshold()
        startBoost(100)
        Thread.sleep(1500)
        val baseline = BoostProbeClient.dumpWithRmsRetry(targetContext, minRms = minRms)
        RmsAssertionSupport.assertSnapshotSignalPresent(baseline, "Baseline@100%")

        startBoost(150)
        Thread.sleep(2000)
        val boosted = BoostProbeClient.dumpWithRmsRetry(targetContext, minRms = minRms)
        RmsAssertionSupport.assertSnapshotSignalPresent(boosted, "Boosted@150%")
        RmsAssertionSupport.assertBoostRatio(baseline.rmsRatio, boosted.rmsRatio)

        assertEquals(
            "Target gain for 150% boost (probe=${boosted.lastReapply})",
            1500,
            boosted.targetGainMb,
        )
        assertTrue(
            "Global effect should be enabled (gain=${boosted.targetGainMb} rms=${boosted.rmsRatio})",
            boosted.globalEffectEnabled,
        )
    }

    private fun startBoost(percent: Int) {
        val trampoline = Intent().apply {
            setClassName(DEBUG_PKG, "$DEBUG_PKG.BoostTrampolineActivity")
            action = "com.deciboost.SET_BOOST"
            putExtra("boost", percent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        targetContext.startActivity(trampoline)
    }
}
