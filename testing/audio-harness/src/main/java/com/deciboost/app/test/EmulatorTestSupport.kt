package com.deciboost.app.test

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

object EmulatorTestSupport {
    /**
     * Emulator merge gate: probe must observe non-silent output (signal-present check).
     * Absolute 0.85 applies when running harness on a physical device (local/CI).
     */
    const val EMULATOR_MIN_SIGNAL = 0.05f

    /** Minimum RMS when the harness runs on a physical device (not an AVD). */
    const val DEVICE_MIN_RMS = 0.85f

    private const val DEBUG_PKG = "com.deciboost.app.debug"
    private const val ACTION_START_HARNESS_TONE = "com.deciboost.DEBUG.START_HARNESS_TONE"
    private const val ACTION_STOP_HARNESS_TONE = "com.deciboost.DEBUG.STOP_HARNESS_TONE"

    fun minRmsThreshold(): Float =
        if (isEmulator()) EMULATOR_MIN_SIGNAL else DEVICE_MIN_RMS

    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("sdk_gphone") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk_gphone") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish")
    }

    fun grantTestPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val shell = instrumentation.uiAutomation
        for (pkg in listOf(
            instrumentation.targetContext.packageName,
            instrumentation.context.packageName,
        )) {
            shell.executeShellCommand("pm grant $pkg android.permission.RECORD_AUDIO")
            shell.executeShellCommand("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        }
    }

    /** Seeds onboarding_complete in the target app via debug broadcast receiver. */
    /** Starts sine-wave playback in the target app process for Visualizer RMS on emulators. */
    fun ensureTargetAppTonePlaying() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val start = Intent(ACTION_START_HARNESS_TONE).apply {
            component = ComponentName(DEBUG_PKG, "$DEBUG_PKG.DebugHarnessToneReceiver")
        }
        instrumentation.targetContext.sendBroadcast(start)
        Thread.sleep(600)
    }

    fun stopTargetAppTone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val stop = Intent(ACTION_STOP_HARNESS_TONE).apply {
            component = ComponentName(DEBUG_PKG, "$DEBUG_PKG.DebugHarnessToneReceiver")
        }
        instrumentation.targetContext.sendBroadcast(stop)
    }

    fun seedOnboardingComplete() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent("com.deciboost.DEBUG.SEED_ONBOARDING").apply {
            component = ComponentName(DEBUG_PKG, "$DEBUG_PKG.DebugTestSetupReceiver")
        }
        // Must originate from targetContext (app UID); shell `am broadcast` is blocked when exported=false.
        instrumentation.targetContext.sendBroadcast(intent)
        Thread.sleep(800)
    }

    fun isTargetRecordAudioGranted(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.targetContext.checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isTestRecordAudioGranted(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.context.checkSelfPermission(
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns null when both target and test APKs have RECORD_AUDIO; otherwise a diagnostic message.
     */
    fun recordAudioGrantFailureMessage(): String? {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetPkg = instrumentation.targetContext.packageName
        val testPkg = instrumentation.context.packageName
        val missing = buildList {
            if (!isTargetRecordAudioGranted()) add(targetPkg)
            if (!isTestRecordAudioGranted()) add(testPkg)
        }
        return if (missing.isEmpty()) {
            null
        } else {
            "RECORD_AUDIO not granted for: ${missing.joinToString()}"
        }
    }

    /**
     * Merge-blocking probe health: always assert RMS when RECORD_AUDIO is granted to the target app.
     */
    fun requiresRmsAssertion(): Boolean = isTargetRecordAudioGranted()
}
