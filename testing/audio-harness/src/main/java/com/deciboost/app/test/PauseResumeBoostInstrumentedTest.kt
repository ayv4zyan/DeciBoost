package com.deciboost.app.test

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PauseResumeBoostInstrumentedTest {

    companion object {
        private const val DEBUG_PKG = "com.deciboost.app.debug"
    }

    @get:Rule
    val grantPermission: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val uiDevice = UiDevice.getInstance(instrumentation)

    @Before
    fun setUp() {
        EmulatorTestSupport.grantTestPermissions()
        EmulatorTestSupport.seedOnboardingComplete()
        RmsAssertionSupport.preflightRecordAudio()

        val trampoline = Intent().apply {
            setClassName(DEBUG_PKG, "$DEBUG_PKG.BoostTrampolineActivity")
            action = "com.deciboost.SET_BOOST"
            putExtra("boost", 150)
        }
        trampoline.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(trampoline)
        Thread.sleep(2000)
    }

    @Test
    fun boostSurvivesPauseResume_withAppBackgrounded() {
        EmulatorTestSupport.ensureTargetAppTonePlaying()
        instrumentation.context.startActivity(
            Intent(instrumentation.context, HarnessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        Thread.sleep(2500)

        val minRms = EmulatorTestSupport.minRmsThreshold()
        val pre = BoostProbeClient.dumpWithRmsRetry(targetContext, minRms = minRms)
        RmsAssertionSupport.assertSnapshotSignalPresent(pre, "Pre")
        assertTrue("Pre: global effect should be enabled", pre.globalEffectEnabled)
        assertEquals("Pre: target gain", 1500, pre.targetGainMb)

        uiDevice.pressHome()
        Thread.sleep(500)

        HarnessActivity.instance?.pausePlayback()
        Thread.sleep(500)
        HarnessActivity.instance?.resumePlayback()
        Thread.sleep(500)

        val post = BoostProbeClient.dumpWithRmsRetry(
            targetContext,
            minRms = minRms,
            timeoutMs = 1500,
        )
        RmsAssertionSupport.assertSnapshotSignalPresent(post, "Post")
        assertTrue("Post: global effect should be enabled within 1500ms", post.globalEffectEnabled)
        assertEquals("Post: target gain unchanged", pre.targetGainMb, post.targetGainMb)
    }
}
