package com.deciboost.feature.boost

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deciboost.core.audio.policy.SessionEffectRegistry
import com.deciboost.feature.boost.ui.theme.BrandCyan
import kotlin.math.abs

private const val WAVEFORM_BYTE_MASK = 0xFF
private const val WAVEFORM_NORMALIZE_DIVISOR = 128f
private const val WAVEFORM_MIN_BAR_HEIGHT = 2f
private const val WAVEFORM_BAR_STROKE_WIDTH = 3f
private val WAVEFORM_BAR_COLOR: Color = BrandCyan

@Composable
fun WaveformVisualizer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPermissionRevoked: () -> Unit = {},
) {
    if (!enabled) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted && enabled) {
                    onPermissionRevoked()
                }
                hasPermission = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasPermission) return

    var waveform by remember { mutableStateOf(ByteArray(0)) }

    DisposableEffect(enabled, hasPermission) {
        val visualizer = openVisualizerOrNull { captured -> waveform = captured }
        onDispose {
            visualizer?.release()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        if (waveform.isEmpty()) return@Canvas
        val step = (waveform.size / size.width.toInt()).coerceAtLeast(1)
        var x = 0f
        var index = 0
        while (index < waveform.size && x < size.width) {
            val normalized = (waveform[index].toInt() and WAVEFORM_BYTE_MASK) /
                WAVEFORM_NORMALIZE_DIVISOR - 1f
            val barHeight = (abs(normalized) * size.height / 2f).coerceAtLeast(WAVEFORM_MIN_BAR_HEIGHT)
            drawLine(
                color = WAVEFORM_BAR_COLOR,
                start = androidx.compose.ui.geometry.Offset(x, size.height / 2f - barHeight),
                end = androidx.compose.ui.geometry.Offset(x, size.height / 2f + barHeight),
                strokeWidth = WAVEFORM_BAR_STROKE_WIDTH,
            )
            x += step * 2f
            index += step
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun openVisualizerOrNull(onWaveform: (ByteArray) -> Unit): Visualizer? {
    return try {
        Visualizer(SessionEffectRegistry.GLOBAL_SESSION).also { viz ->
            viz.captureSize = Visualizer.getCaptureSizeRange()[1]
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveformData: ByteArray?,
                        samplingRate: Int,
                    ) {
                        waveformData?.let { onWaveform(it.copyOf()) }
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
            viz.enabled = true
        }
    } catch (_: IllegalStateException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        // Visualizer attach fails on some OEM/session stacks; visualizer is optional UI.
        null
    }
}
