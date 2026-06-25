package com.deciboost.feature.boost

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deciboost.core.audio.policy.PlaybackPhase
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

    val boostContent: @Composable (Modifier, Boolean) -> Unit = { modifier, showSettingsButton ->
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
            onNavigateToSettings = onNavigateToSettings,
            onPermissionRevoked = viewModel::syncVisualizerPermission,
            showSettingsButton = showSettingsButton,
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
                        boostContent(Modifier.fillMaxHeight(), false)
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
            boostContent(Modifier.padding(padding), true)
        }
    }
}

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
    onNavigateToSettings: () -> Unit,
    onPermissionRevoked: () -> Unit,
    showSettingsButton: Boolean = true,
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
        ArcBoostGauge(percent = animatedPercent)

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

        AssistChip(
            onClick = {},
            label = { Text(outputDevice) },
            leadingIcon = {
                Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.semantics {
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                contentDescription = "Output device: $outputDevice"
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(playbackPhase.name) },
                leadingIcon = {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            AssistChip(
                onClick = {},
                label = { Text(if (isHealthy) "Engine OK" else "Engine issue") },
            )
        }

        WaveformVisualizer(
            enabled = visualizerEnabled,
            onPermissionRevoked = onPermissionRevoked,
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

        if (showSettingsButton) {
            TextButton(onClick = onNavigateToSettings) {
                Text("Open Settings")
            }
        }
    }
}

private val GAUGE_TRACK_COLOR = androidx.compose.ui.graphics.Color(0xFF2A2F3A)
private val GAUGE_GRADIENT_START = androidx.compose.ui.graphics.Color(0xFF7C4DFF)
private val GAUGE_GRADIENT_MID = androidx.compose.ui.graphics.Color(0xFF18FFFF)
private val GAUGE_GRADIENT_END = androidx.compose.ui.graphics.Color(0xFFFF6E40)
private const val GAUGE_ARC_START_ANGLE = 135f
private const val GAUGE_ARC_SWEEP_MAX = 270f

@Composable
private fun ArcBoostGauge(percent: Float) {
    val sweep = ((percent - 100f) / 100f) * GAUGE_ARC_SWEEP_MAX
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 18f
            val diameter = size.minDimension - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = GAUGE_TRACK_COLOR,
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
                        GAUGE_GRADIENT_START,
                        GAUGE_GRADIENT_MID,
                        GAUGE_GRADIENT_END,
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
                color = androidx.compose.ui.graphics.Color.White,
                radius = 10f,
                center = Offset(cx, cy),
            )
        }
    }
}

