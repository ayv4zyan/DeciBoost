package com.deciboost.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.deciboost.core.data.ThemeStyle

// Swatch colors mirror fixed OLED themes (display-only).
private val SwatchViolet = Color(0xFF7B5CF0)
private val SwatchCyan = Color(0xFF2EE6F0)
private val SwatchWarm = Color(0xFFE8A020)
private val SwatchScope = Color(0xFF3DFF6E)
private val OledSwatchBlack = Color(0xFF000000)

/**
 * Appearance theme list: Material You (wallpaper) + fixed OLED accent styles.
 */
@Composable
fun ThemePicker(
    selected: ThemeStyle,
    onSelect: (ThemeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        ThemeStyle.entries.forEach { style ->
            val meta = themeMeta(style, dynamicAvailable)
            ThemeOptionRow(
                title = meta.title,
                subtitle = meta.subtitle,
                swatch = meta.swatch,
                selected = selected == style,
                enabled = meta.enabled,
                onClick = { if (meta.enabled) onSelect(style) },
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    swatch: ThemeSwatch,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemeSwatchDot(swatch = swatch, selected = selected, modifier = Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
    }
}

@Composable
private fun ThemeSwatchDot(
    swatch: ThemeSwatch,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .background(
                brush = when (swatch) {
                    is ThemeSwatch.Solid -> Brush.linearGradient(listOf(swatch.color, swatch.color))
                    is ThemeSwatch.Gradient -> Brush.sweepGradient(swatch.colors)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private sealed class ThemeSwatch {
    data class Solid(val color: Color) : ThemeSwatch()
    data class Gradient(val colors: List<Color>) : ThemeSwatch()
}

private data class ThemeOptionMeta(
    val title: String,
    val subtitle: String,
    val swatch: ThemeSwatch,
    val enabled: Boolean = true,
)

private fun themeMeta(style: ThemeStyle, dynamicAvailable: Boolean): ThemeOptionMeta = when (style) {
    ThemeStyle.DYNAMIC -> ThemeOptionMeta(
        title = "System colors",
        subtitle = if (dynamicAvailable) {
            "From your wallpaper (Material You)"
        } else {
            "Requires Android 12 or newer"
        },
        swatch = ThemeSwatch.Gradient(
            listOf(SwatchViolet, SwatchCyan, SwatchWarm, SwatchScope, SwatchViolet),
        ),
        enabled = dynamicAvailable,
    )
    ThemeStyle.DYNAMIC_OLED -> ThemeOptionMeta(
        title = "System colors · OLED",
        subtitle = if (dynamicAvailable) {
            "Wallpaper accents · true black surfaces"
        } else {
            "Requires Android 12 or newer"
        },
        swatch = ThemeSwatch.Gradient(
            listOf(OledSwatchBlack, SwatchViolet, SwatchCyan, SwatchWarm, OledSwatchBlack),
        ),
        enabled = dynamicAvailable,
    )
    ThemeStyle.VIOLET -> ThemeOptionMeta(
        title = "Violet",
        subtitle = "OLED black · purple accents",
        swatch = ThemeSwatch.Solid(SwatchViolet),
    )
    ThemeStyle.CYAN -> ThemeOptionMeta(
        title = "Cyan Peak",
        subtitle = "OLED black · logo cyan",
        swatch = ThemeSwatch.Solid(SwatchCyan),
    )
    ThemeStyle.WARM -> ThemeOptionMeta(
        title = "Warm VU",
        subtitle = "OLED black · amber studio",
        swatch = ThemeSwatch.Solid(SwatchWarm),
    )
    ThemeStyle.SCOPE -> ThemeOptionMeta(
        title = "Scope Green",
        subtitle = "OLED black · phosphor green",
        swatch = ThemeSwatch.Solid(SwatchScope),
    )
}
