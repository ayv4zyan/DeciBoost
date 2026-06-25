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
import kotlin.math.abs

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
        val visualizer = try {
            Visualizer(SessionEffectRegistry.GLOBAL_SESSION).also { viz ->
                viz.captureSize = Visualizer.getCaptureSizeRange()[1]
                viz.setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveformData: ByteArray?,
                            samplingRate: Int,
                        ) {
                            waveformData?.let { waveform = it.copyOf() }
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
        } catch (_: Exception) {
            null
        }
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
            val normalized = (waveform[index].toInt() and 0xFF) / 128f - 1f
            val barHeight = (abs(normalized) * size.height / 2f).coerceAtLeast(2f)
            drawLine(
                color = Color(0xFF18FFFF),
                start = androidx.compose.ui.geometry.Offset(x, size.height / 2f - barHeight),
                end = androidx.compose.ui.geometry.Offset(x, size.height / 2f + barHeight),
                strokeWidth = 3f,
            )
            x += step * 2f
            index += step
        }
    }
}