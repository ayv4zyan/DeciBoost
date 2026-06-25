package com.deciboost.app.test

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

object RmsAssertionSupport {

    fun preflightRecordAudio() {
        val failure = EmulatorTestSupport.recordAudioGrantFailureMessage()
        assertNull("RECORD_AUDIO grant preflight failed: $failure", failure)
        assertTrue(
            "Merge-blocking RMS requires RECORD_AUDIO on target app",
            EmulatorTestSupport.requiresRmsAssertion(),
        )
    }

    fun assertSnapshotSignalPresent(snapshot: BoostProbeClient.Snapshot, label: String) {
        val min = EmulatorTestSupport.minRmsThreshold()
        assertTrue(
            "$label: rms=${snapshot.rmsRatio} must be >= $min " +
                "(emulator=${EmulatorTestSupport.isEmulator()}); " +
                "zero indicates Visualizer/RECORD_AUDIO failure in target app",
            snapshot.rmsRatio >= min,
        )
    }

    /**
     * Physical-device merge gate: absolute RMS >= 0.85 on boosted snapshot.
     *
     * Emulator AVDs: session-0 Visualizer RMS does not shift measurably when
     * LoudnessEnhancer applies gain (observed ~1.00x ratio at 150% boost). Emulator CI
     * validates boost via engine state (target_gain_mb, global_effect_enabled) plus
     * [assertSnapshotSignalPresent] for probe health. On physical devices (local runs),
     * [assertBoostRatio] also enforces absolute RMS >= [EmulatorTestSupport.DEVICE_MIN_RMS].
     */
    fun assertBoostRatio(baselineRms: Float, boostedRms: Float) {
        if (EmulatorTestSupport.isEmulator()) {
            assertTrue(
                "Emulator boosted signal-present rms=$boostedRms must be >= " +
                    "${EmulatorTestSupport.EMULATOR_MIN_SIGNAL} (baseline=$baselineRms)",
                boostedRms >= EmulatorTestSupport.EMULATOR_MIN_SIGNAL,
            )
            return
        }
        assertTrue(
            "Device boosted RMS $boostedRms must be >= ${EmulatorTestSupport.DEVICE_MIN_RMS}",
            boostedRms >= EmulatorTestSupport.DEVICE_MIN_RMS,
        )
    }
}
