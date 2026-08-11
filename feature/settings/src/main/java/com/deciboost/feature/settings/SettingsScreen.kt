package com.deciboost.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val autoStart by viewModel.autoStartOnBoot.collectAsStateWithLifecycle()
    val gradual by viewModel.gradualBoost.collectAsStateWithLifecycle()
    val pauseNonMedia by viewModel.pauseOnNonMedia.collectAsStateWithLifecycle()
    val killSwitch by viewModel.killSwitchEnabled.collectAsStateWithLifecycle()
    val visualizer by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
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

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setVisualizerEnabled(true)
        }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    BackHandler(onBack = onBack)

    var safetyExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SafetyNotesBanner(
                expanded = safetyExpanded,
                onToggle = { safetyExpanded = !safetyExpanded },
            )

            SettingsSectionLabel("Appearance")
            ThemePicker(
                selected = themeStyle,
                onSelect = viewModel::setThemeStyle,
            )

            SettingsSectionLabel("Behavior")
            SettingToggle(
                title = "Auto-start on boot",
                subtitle = "Restore notification after reboot — never silent boost",
                checked = autoStart,
                onCheckedChange = viewModel::setAutoStartOnBoot,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            SettingToggle(
                title = "Gradual boost",
                subtitle = "Ramp smoothly when changing levels",
                checked = gradual,
                onCheckedChange = viewModel::setGradualBoost,
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            SettingToggle(
                title = "Pause boost for non-media",
                subtitle = "Disable during notification-dominant playback",
                checked = pauseNonMedia,
                onCheckedChange = viewModel::setPauseOnNonMedia,
            )

            SettingsSectionLabel("Extras")
            SettingToggle(
                title = "Waveform visualizer",
                subtitle = "Live waveform — needs microphone permission",
                checked = visualizer,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.setVisualizerEnabled(true)
                        }
                    } else {
                        viewModel.setVisualizerEnabled(false)
                    }
                },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            SettingToggle(
                title = "Kill switch",
                subtitle = "Emergency rollback to 100%",
                checked = killSwitch,
                onCheckedChange = viewModel::setKillSwitchEnabled,
            )

            SettingsSectionLabel("System")
            SettingsActionRow(
                title = "Battery optimization",
                subtitle = "Keep the boost service alive in background",
                onClick = { batteryLauncher.launch(viewModel.createBatteryOptimizationIntent()) },
                leadingIcon = {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            SettingsActionRow(
                title = "About DeciBoost",
                subtitle = "Version, license, privacy",
                onClick = onNavigateToAbout,
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SafetyNotesBanner(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { role = Role.Button },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Safety notes",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Hearing · global mix · TalkBack",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SafetyDetail(
                        "Hearing safety",
                        "Boost above 100% can damage hearing and speakers. DeciBoost offers up to 200% with safety prompts — use responsibly.",
                    )
                    SafetyDetail(
                        "Global side effects",
                        "Session-0 boost amplifies all output-mix audio including notifications and games. Enable “Pause for non-media” to reduce this.",
                    )
                    SafetyDetail(
                        "Accessibility",
                        "TalkBack audio routed through the media mixer may be amplified. Consider keeping boost ≤150% when TalkBack is enabled.",
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyDetail(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
