package com.deciboost.feature.boost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D2F6B),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = BrandCyan,
    onSecondary = Color(0xFF003640),
    secondaryContainer = Color(0xFF1A4A55),
    onSecondaryContainer = Color(0xFFB8F4FF),
    tertiary = BrandBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2A3F70),
    onTertiaryContainer = Color(0xFFD6E4FF),
    background = NavyBlack,
    onBackground = OnSurfaceLight,
    surface = NavySurface,
    onSurface = OnSurfaceLight,
    surfaceVariant = NavyElevated,
    onSurfaceVariant = OnSurfaceMuted,
    outline = NavyVariant,
    outlineVariant = Color(0xFF3A3D5C),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5E35B1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF2A1060),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F4FF),
    onSecondaryContainer = Color(0xFF003640),
    tertiary = Color(0xFF2A5299),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E4FF),
    onTertiaryContainer = Color(0xFF0D2147),
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF1A1C28),
    surface = Color.White,
    onSurface = Color(0xFF1A1C28),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF45465A),
    outline = Color(0xFF757689),
    outlineVariant = Color(0xFFC5C6D4),
)

@Composable
fun DeciBoostTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
