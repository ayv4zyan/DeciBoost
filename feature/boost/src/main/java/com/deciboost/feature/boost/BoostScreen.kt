package com.deciboost.feature.boost

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deciboost.core.audio.policy.PlaybackPhase
import com.deciboost.feature.boost.ui.theme.LocalBrandAccents
import com.deciboost.feature.settings.SettingsSummaryPanel
import com.deciboost.feature.settings.SettingsSummaryState
import kotlin.math.cos
import kotlin.math.sin

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun BoostScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: BoostViewModel = hiltViewModel(),
) {
    val boostState by viewModel.boostState.collectAsStateWithLifecycle()
    val playbackPhase by viewModel.playbackPhase.collectAsStateWithLifecycle()
    val safetyDialog by viewModel.safetyDialog.collectAsStateWithLifecycle()
    val sliderPercent by viewModel.sliderPercent.collectAsStateWithLifecycle()
    val volumePercent by viewModel.volumePercent.collectAsStateWithLifecycle()
    val outputDevice by viewModel.outputDeviceLabel.collectAsStateWithLifecycle()
    val visualizerEnabled by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsStateWithLifecycle()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsStateWithLifecycle()
    val gradualBoost by viewModel.gradualBoost.collectAsStateWithLifecycle()
    val pauseOnNonMedia by viewModel.pauseOnNonMedia.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncVisualizerPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    val windowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Medium ||
        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded

    safetyDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSafetyDialog() },
            title = { Text("Safety Warning") },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSafetyDialog() }) {
                    Text("I understand")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSafetyDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }

    val boostContent: @Composable (Modifier) -> Unit = { modifier ->
        BoostContent(
            modifier = modifier,
            percent = sliderPercent,
            volumePercent = volumePercent,
            outputDevice = outputDevice,
            playbackPhase = playbackPhase,
            isHealthy = boostState.globalEffectHealthy,
            visualizerEnabled = visualizerEnabled,
            boostSliderEnabled = !killSwitchEnabled,
            onPercentChange = { viewModel.onBoostChanged(it.toInt()) },
            onVolumeChange = viewModel::onVolumeChanged,
            shouldPerformHaptic = viewModel::shouldPerformHaptic,
            onPermissionRevoked = viewModel::syncVisualizerPermission,
        )
    }

    if (isExpanded) {
        val navigator = rememberListDetailPaneScaffoldNavigator()
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text("DeciBoost", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            ListDetailPaneScaffold(
                directive = navigator.scaffoldDirective,
                value = navigator.scaffoldValue,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                listPane = {
                    AnimatedPane {
                        boostContent(Modifier.fillMaxHeight())
                    }
                },
                detailPane = {
                    AnimatedPane {
                        SettingsSummaryPanel(
                            modifier = Modifier.fillMaxHeight(),
                            outputDevice = outputDevice,
                            settingsState = SettingsSummaryState(
                                autoStartOnBoot = autoStartOnBoot,
                                gradualBoost = gradualBoost,
                                pauseOnNonMedia = pauseOnNonMedia,
                                killSwitchEnabled = killSwitchEnabled,
                            ),
                            onAutoStartChange = viewModel::setAutoStartOnBoot,
                            onGradualBoostChange = viewModel::setGradualBoost,
                            onPauseOnNonMediaChange = viewModel::setPauseOnNonMedia,
                            onKillSwitchChange = viewModel::setKillSwitchEnabled,
                            onNavigateToSettings = onNavigateToSettings,
                        )
                    }
                },
            )
        }
    } else {
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = { Text("DeciBoost", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            boostContent(Modifier.padding(padding))
        }
    }
}

/**
 * Home layout: same hierarchy as before, except the opt-in waveform is drawn
 * inside the boost gauge (issue #9 — variant C waveform position only).
 */
@Composable
private fun BoostContent(
    modifier: Modifier = Modifier,
    percent: Int,
    volumePercent: Int,
    outputDevice: String,
    playbackPhase: PlaybackPhase,
    isHealthy: Boolean,
    visualizerEnabled: Boolean,
    boostSliderEnabled: Boolean,
    onPercentChange: (Float) -> Unit,
    onVolumeChange: (Int) -> Unit,
    shouldPerformHaptic: (Int) -> Boolean,
    onPermissionRevoked: () -> Unit,
) {
    val animatedPercent by animateFloatAsState(percent.toFloat(), label = "boost")
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ArcBoostGauge(
            percent = animatedPercent,
            visualizerEnabled = visualizerEnabled,
            onPermissionRevoked = onPermissionRevoked,
        )

        Text(
            text = "${percent}%",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Media boost above system max",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Slider(
            value = percent.toFloat(),
            onValueChange = { value ->
                val snapped = value.toInt()
                if (shouldPerformHaptic(snapped)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onPercentChange(value)
            },
            enabled = boostSliderEnabled,
            valueRange = 100f..200f,
            steps = 19,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Boost slider, $percent percent" },
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("System volume", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = volumePercent.toFloat(),
                onValueChange = { onVolumeChange(it.toInt()) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "System volume, $volumePercent percent" },
            )
            Text(
                text = "$volumePercent%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        SessionStatusStrip(
            outputDevice = outputDevice,
            playbackPhase = playbackPhase,
            isHealthy = isHealthy,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!isHealthy) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = "Audio effect unavailable on this device. Try lowering boost.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

private const val GAUGE_ARC_START_ANGLE = 135f
private const val GAUGE_ARC_SWEEP_MAX = 270f
private val GaugeInnerWaveWidth = 100.dp
private val GaugeInnerWaveHeight = 56.dp

/** Read-only session indicators (issue #7 variant A) — no press affordance. */
@Composable
private fun SessionStatusStrip(
    outputDevice: String,
    playbackPhase: PlaybackPhase,
    isHealthy: Boolean,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val engineLabel = if (isHealthy) "OK" else "Issue"
    val playbackOk = playbackPhase == PlaybackPhase.Active

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription =
                "Status: output $outputDevice, playback ${playbackPhase.name}, engine $engineLabel"
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusKeyValueRow(label = "Output", labelColor = muted) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = outputDevice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StatusKeyValueRow(label = "Playback", labelColor = muted) {
                StatusDot(
                    color = if (playbackOk) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
                Text(
                    text = playbackPhase.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            StatusKeyValueRow(label = "Engine", labelColor = muted) {
                StatusDot(
                    color = if (isHealthy) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = engineLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Status only",
                style = MaterialTheme.typography.labelSmall,
                color = muted.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusKeyValueRow(
    label: String,
    labelColor: Color,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            letterSpacing = 0.05.em,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            value()
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(color = color, shape = CircleShape),
    )
}

@Composable
private fun ArcBoostGauge(
    percent: Float,
    visualizerEnabled: Boolean,
    onPermissionRevoked: () -> Unit,
) {
    val accents = LocalBrandAccents.current
    val sweep = ((percent - 100f) / 100f) * GAUGE_ARC_SWEEP_MAX
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18f
            val diameter = size.minDimension - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = accents.gaugeTrack,
                startAngle = GAUGE_ARC_START_ANGLE,
                sweepAngle = GAUGE_ARC_SWEEP_MAX,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        accents.gaugeStart,
                        accents.gaugeMid,
                        accents.gaugeEnd,
                    ),
                ),
                startAngle = GAUGE_ARC_START_ANGLE,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val angleRad = Math.toRadians((135 + sweep).toDouble())
            val radius = diameter / 2
            val cx = size.width / 2 + radius * cos(angleRad).toFloat()
            val cy = size.height / 2 + radius * sin(angleRad).toFloat()
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = Offset(cx, cy),
            )
        }
        // Waveform only: live inset inside the arc (issue #9 C position).
        WaveformVisualizer(
            enabled = visualizerEnabled,
            onPermissionRevoked = onPermissionRevoked,
            modifier = Modifier
                .width(GaugeInnerWaveWidth)
                .height(GaugeInnerWaveHeight),
        )
    }
}

