package com.deciboost.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SettingsSummaryState(
    val autoStartOnBoot: Boolean,
    val gradualBoost: Boolean,
    val pauseOnNonMedia: Boolean,
    val killSwitchEnabled: Boolean,
)

@Composable
fun SettingsSummaryPanel(
    outputDevice: String,
    settingsState: SettingsSummaryState,
    onAutoStartChange: (Boolean) -> Unit,
    onGradualBoostChange: (Boolean) -> Unit,
    onPauseOnNonMediaChange: (Boolean) -> Unit,
    onKillSwitchChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Device & Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Output: $outputDevice",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingToggle(
            title = "Auto-start on boot",
            subtitle = "Restore notification after reboot",
            checked = settingsState.autoStartOnBoot,
            onCheckedChange = onAutoStartChange,
        )
        SettingToggle(
            title = "Gradual boost",
            subtitle = "Ramp boost smoothly when changing levels",
            checked = settingsState.gradualBoost,
            onCheckedChange = onGradualBoostChange,
        )
        SettingToggle(
            title = "Pause boost for non-media",
            subtitle = "Disable boost during notification-dominant playback",
            checked = settingsState.pauseOnNonMedia,
            onCheckedChange = onPauseOnNonMediaChange,
        )
        SettingToggle(
            title = "Kill switch",
            subtitle = "Emergency rollback to 100%",
            checked = settingsState.killSwitchEnabled,
            onCheckedChange = onKillSwitchChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onNavigateToSettings) {
            Text("Full Settings")
        }
    }
}