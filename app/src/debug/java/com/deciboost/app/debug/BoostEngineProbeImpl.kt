package com.deciboost.app.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.deciboost.core.audio.policy.BoostEngine
import com.deciboost.core.audio.policy.ReapplyReason
import com.deciboost.core.audio.policy.SessionEffectRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class BoostEngineProbeImpl @Inject constructor(
    private val engine: BoostEngine,
    @ApplicationContext private val context: Context,
) : BoostEngineProbe {

    private val probeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DeciBoost-Probe").apply { isDaemon = true }
    }

    override fun getGlobalTargetGainMilliBel(): Int {
        return engine.getState().effectiveGainMilliBel
    }

    override fun isGlobalEffectEnabled(): Boolean {
        val state = engine.getState()
        return state.isEnabled && state.globalEffectHealthy
    }

    override fun getLastReapplyReason(): ReapplyReason {
        return engine.getLastReapplyReason()
    }

    override fun measureRmsRatio(): Float {
        if (!isRecordAudioGranted()) {
            Log.w(TAG, "measureRmsRatio: RECORD_AUDIO not granted to ${context.packageName}")
            return 0f
        }
        return try {
            probeExecutor.submit(Callable { sampleRmsOnBackgroundThread() })
                .get(PROBE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "measureRmsRatio failed: ${e.message}")
            0f
        }
    }

    private fun isRecordAudioGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun sampleRmsOnBackgroundThread(): Float {
        var visualizer: Visualizer? = null
        return try {
            visualizer = Visualizer(SessionEffectRegistry.GLOBAL_SESSION)
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer.captureSize = captureSize

            val peak = AtomicReference(0f)
            val latch = CountDownLatch(1)
            val samples = AtomicInteger(0)

            visualizer.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) {
                        waveform ?: return
                        peak.updateAndGet { current -> maxOf(current, computeRms(waveform)) }
                        if (samples.incrementAndGet() >= TARGET_SAMPLES) {
                            latch.countDown()
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false,
            )
            visualizer.enabled = true

            latch.await(LISTENER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            var result = peak.get()

            // Emulator stacks may not deliver listener callbacks promptly — poll waveform directly.
            if (result < MIN_REPORTABLE_RMS) {
                val waveform = ByteArray(captureSize)
                repeat(POLL_ATTEMPTS) {
                    if (visualizer.getWaveForm(waveform) == Visualizer.SUCCESS) {
                        result = maxOf(result, computeRms(waveform))
                        if (result >= MIN_REPORTABLE_RMS) return@repeat
                    }
                    Thread.sleep(POLL_DELAY_MS)
                }
            }

            result.coerceIn(0f, 1f)
        } catch (e: SecurityException) {
            Log.w(TAG, "Visualizer SecurityException: ${e.message}")
            0f
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer sampling failed: ${e.message}")
            0f
        } finally {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (_: Exception) {
                // Best-effort cleanup on probe path.
            }
        }
    }

    private fun computeRms(waveform: ByteArray): Float {
        if (waveform.isEmpty()) return 0f
        var sum = 0.0
        for (sample in waveform) {
            val deviation = (sample.toInt() and 0xFF) - 128
            sum += deviation.toDouble() * deviation
        }
        return (sqrt(sum / waveform.size) / 128.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "DeciBoostProbe"
        private const val LISTENER_TIMEOUT_MS = 1200L
        private const val PROBE_CALL_TIMEOUT_MS = 2000L
        private const val TARGET_SAMPLES = 4
        private const val POLL_ATTEMPTS = 20
        private const val POLL_DELAY_MS = 75L
        private const val MIN_REPORTABLE_RMS = 0.01f
    }
}