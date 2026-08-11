package com.deciboost.feature.boost

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.deciboost.core.audio.policy.SessionEffectRegistry
import com.deciboost.feature.boost.ui.theme.LocalBrandAccents
import kotlin.math.abs
import kotlin.math.exp

private const val WAVEFORM_BYTE_MASK = 0xFF
private const val WAVEFORM_CENTER = 128f
private const val BAR_COUNT = 12
private const val MIN_BAR_HEIGHT_PX = 3f
private const val ATTACK_RATE = 18f
private const val SPRING_STIFFNESS = 28f
private const val SPRING_DAMPING = 8f
private const val BAR_GAP_PX = 3f
private const val TIP_GLOW_ALPHA = 0.25f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val DEFAULT_FRAME_DT = 1f / 60f
private const val MIN_FRAME_DT = 0.001f
private const val MAX_FRAME_DT = 0.05f

/**
 * Live output visualizer (opt-in [enabled] + RECORD_AUDIO).
 *
 * Fixed-position center-bounce bars (issue #16): each bar keeps its X and only
 * height animates — fast attack toward peaks, light spring settle on release.
 * Display loop runs every frame so motion is continuous, not Visualizer-rate.
 *
 * Size is fully controlled by [modifier] — e.g. full-width strip or compact
 * inset inside the boost gauge (issue #9 variant C).
 */
@Composable
fun WaveformVisualizer(
    enabled: Boolean,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(64.dp),
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

    val liveTargets = remember { FloatArray(BAR_COUNT) }
    val heights = remember { FloatArray(BAR_COUNT) }
    val velocities = remember { FloatArray(BAR_COUNT) }
    var frame by remember { mutableIntStateOf(0) }

    DisposableEffect(enabled, hasPermission) {
        val visualizer = openVisualizerOrNull { captured ->
            bucketWaveform(captured, liveTargets)
        }
        onDispose {
            visualizer?.release()
        }
    }

    LaunchedEffect(enabled, hasPermission) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) {
                    DEFAULT_FRAME_DT
                } else {
                    ((nanos - lastNanos) / NANOS_PER_SECOND).coerceIn(MIN_FRAME_DT, MAX_FRAME_DT)
                }
                lastNanos = nanos
                stepBarHeights(liveTargets, heights, velocities, dt)
                frame++
            }
        }
    }

    val barColor = LocalBrandAccents.current.waveform
    val frameTick = frame

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION")
        frameTick
        drawCenterBounceBars(heights, barColor)
    }
}

private fun stepBarHeights(
    targets: FloatArray,
    heights: FloatArray,
    velocities: FloatArray,
    dt: Float,
) {
    for (i in heights.indices) {
        val target = targets[i].coerceIn(0f, 1f)
        val current = heights[i]
        if (target > current) {
            // Fast jump up toward the peak.
            heights[i] = current + (target - current) * (1f - exp(-ATTACK_RATE * dt))
            velocities[i] = 0f
        } else {
            // Light spring settle back toward the target.
            val force = (target - current) * SPRING_STIFFNESS - velocities[i] * SPRING_DAMPING
            velocities[i] += force * dt
            heights[i] = (current + velocities[i] * dt).coerceAtLeast(0f)
        }
    }
}

private fun DrawScope.drawCenterBounceBars(bars: FloatArray, color: Color) {
    if (bars.isEmpty() || size.width < 1f) return
    val count = bars.size
    val barWidth = ((size.width - BAR_GAP_PX * (count - 1)) / count).coerceAtLeast(3f)
    val total = count * barWidth + (count - 1) * BAR_GAP_PX
    val startX = ((size.width - total) / 2f).coerceAtLeast(0f)
    val midY = size.height / 2f
    val maxH = midY - 2f

    for (i in bars.indices) {
        val level = bars[i].coerceIn(0f, 1f)
        val h = (level * maxH).coerceAtLeast(MIN_BAR_HEIGHT_PX)
        val x = startX + i * (barWidth + BAR_GAP_PX)
        drawRoundRect(
            color = color,
            topLeft = Offset(x, midY - h),
            size = Size(barWidth, h * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
        drawCircle(
            color = color.copy(alpha = TIP_GLOW_ALPHA * level),
            radius = barWidth * 0.85f,
            center = Offset(x + barWidth / 2f, midY - h),
        )
        drawCircle(
            color = color.copy(alpha = TIP_GLOW_ALPHA * level),
            radius = barWidth * 0.85f,
            center = Offset(x + barWidth / 2f, midY + h),
        )
    }
}

private fun bucketWaveform(bytes: ByteArray, out: FloatArray) {
    if (bytes.isEmpty()) return
    val bucket = (bytes.size / out.size).coerceAtLeast(1)
    for (i in out.indices) {
        var peak = 0f
        val start = i * bucket
        val end = minOf(start + bucket, bytes.size)
        for (j in start until end) {
            val n = abs((bytes[j].toInt() and WAVEFORM_BYTE_MASK) / WAVEFORM_CENTER - 1f)
            if (n > peak) peak = n
        }
        out[i] = peak.coerceIn(0f, 1f)
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
