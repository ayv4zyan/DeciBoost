package com.deciboost.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WarningCard(
                title = "Hearing safety",
                body = "Boost above 100% can damage hearing and speakers. DeciBoost offers up to 200% with safety prompts — use responsibly.",
            )
            WarningCard(
                title = "Global side effects",
                body = "Session-0 boost amplifies all output-mix audio including notifications and games. Enable 'Pause for non-media' to reduce this.",
            )
            WarningCard(
                title = "Accessibility",
                body = "TalkBack audio routed through the media mixer may be amplified. Consider keeping boost ≤150% when TalkBack is enabled.",
            )

            Button(
                onClick = { batteryLauncher.launch(viewModel.createBatteryOptimizationIntent()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Battery optimization settings")
            }

            SettingToggle(
                title = "Auto-start on boot",
                subtitle = "Shows a restore notification after reboot — never applies boost silently",
                checked = autoStart,
                onCheckedChange = viewModel::setAutoStartOnBoot,
            )
            SettingToggle(
                title = "Gradual boost",
                subtitle = "Ramp boost smoothly when changing levels",
                checked = gradual,
                onCheckedChange = viewModel::setGradualBoost,
            )
            SettingToggle(
                title = "Pause boost for non-media",
                subtitle = "Temporarily disable boost during notification-dominant playback",
                checked = pauseNonMedia,
                onCheckedChange = viewModel::setPauseOnNonMedia,
            )
            SettingToggle(
                title = "Waveform visualizer",
                subtitle = "Opt-in live waveform — requires microphone permission for Visualizer API",
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
            SettingToggle(
                title = "Kill switch",
                subtitle = "Emergency rollback — stops service and resets boost to 100%",
                checked = killSwitch,
                onCheckedChange = viewModel::setKillSwitchEnabled,
            )

            Button(
                onClick = onNavigateToAbout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("About DeciBoost")
            }
        }
    }
}

@Composable
private fun WarningCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

