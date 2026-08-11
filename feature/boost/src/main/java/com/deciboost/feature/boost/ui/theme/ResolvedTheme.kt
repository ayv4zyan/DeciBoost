package com.deciboost.feature.boost.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import com.deciboost.core.data.ThemeStyle

/**
 * Resolved Material scheme + custom gauge/waveform accents for a [ThemeStyle].
 * Fixed styles and [ThemeStyle.DYNAMIC_OLED] use an OLED-black chassis;
 * [ThemeStyle.DYNAMIC] uses full Material You surfaces from the system.
 */
data class ResolvedTheme(
    val colorScheme: ColorScheme,
    val accents: BrandAccents,
)

// —— OLED chassis (fixed themes + System colors · OLED) ——
private val OledBlack = Color(0xFF000000)
private val OledSurface = Color(0xFF0A0A0A)
private val OledPanel = Color(0xFF141414)
private val OledPanelHigh = Color(0xFF1A1A1A)
private val OledPanelHighest = Color(0xFF222222)
private val OledOutline = Color(0xFF2E2E2E)
private val OledOutlineVariant = Color(0xFF1E1E1E)
private val OledOn = Color(0xFFF0F0F0)
private val OledMuted = Color(0xFFA8A8A8)
private val OledGaugeTrack = Color(0xFF1E1E1E)
private val White = Color.White

// —— Violet theme ——
private val VioletPrimaryContainer = Color(0xFF3D2F6B)
private val VioletOnPrimaryContainer = Color(0xFFE8DEFF)
private val VioletOnSecondary = Color(0xFF003640)
private val VioletSecondaryContainer = Color(0xFF1A4A55)
private val VioletOnSecondaryContainer = Color(0xFFB8F4FF)
private val VioletTertiaryContainer = Color(0xFF2A3F70)
private val VioletOnTertiaryContainer = Color(0xFFD6E4FF)

// —— Cyan Peak ——
private val CyanPrimary = Color(0xFF2EE6F0)
private val CyanOnPrimary = Color(0xFF00363B)
private val CyanPrimaryContainer = Color(0xFF0A3D44)
private val CyanOnPrimaryContainer = Color(0xFFB8F8FF)
private val CyanTeal = Color(0xFF1AB8A8)
private val CyanOnSecondary = Color(0xFF003730)
private val CyanSecondaryContainer = Color(0xFF0E3D38)
private val CyanOnSecondaryContainer = Color(0xFFA8F0E6)
private val CyanTertiary = Color(0xFF7EB6FF)
private val CyanOnTertiary = Color(0xFF002B4D)
private val CyanTertiaryContainer = Color(0xFF1A3A5C)
private val CyanOnTertiaryContainer = Color(0xFFD0E4FF)
private val CyanGaugeEnd = Color(0xFFB8FFFF)

// —— Warm VU ——
private val WarmAmber = Color(0xFFE8A020)
private val WarmOnPrimary = Color(0xFF2A1800)
private val WarmPrimaryContainer = Color(0xFF5C3A08)
private val WarmOnPrimaryContainer = Color(0xFFFFE2A8)
private val WarmCopper = Color(0xFFC46A3A)
private val WarmOnSecondary = Color(0xFF2A1008)
private val WarmSecondaryContainer = Color(0xFF4A2818)
private val WarmOnSecondaryContainer = Color(0xFFFFD0BC)
private val WarmTertiary = Color(0xFFD4A574)
private val WarmOnTertiary = Color(0xFF2A1C0C)
private val WarmTertiaryContainer = Color(0xFF4A3820)
private val WarmOnTertiaryContainer = Color(0xFFFFE8C8)
private val WarmGaugeEnd = Color(0xFFFFE8B0)

// —— Scope Green ——
private val ScopeGreen = Color(0xFF3DFF6E)
private val ScopeOnPrimary = Color(0xFF003314)
private val ScopePrimaryContainer = Color(0xFF0A3D1C)
private val ScopeOnPrimaryContainer = Color(0xFFB8FFCC)
private val ScopeSecondary = Color(0xFF8AFFB0)
private val ScopeOnSecondary = Color(0xFF003318)
private val ScopeSecondaryContainer = Color(0xFF1A4A2A)
private val ScopeOnSecondaryContainer = Color(0xFFC8FFD8)
private val ScopeTertiary = Color(0xFFB0B0B0)
private val ScopeTertiaryContainer = Color(0xFF2A2A2A)
private val ScopeOnTertiaryContainer = Color(0xFFE8E8E8)
private val ScopeGaugeStart = Color(0xFF1A9940)
private val ScopeGaugeEnd = Color(0xFFB8FFCC)

fun resolveTheme(context: Context, style: ThemeStyle): ResolvedTheme = when (style) {
    ThemeStyle.DYNAMIC -> resolveDynamic(context, oledChassis = false)
    ThemeStyle.DYNAMIC_OLED -> resolveDynamic(context, oledChassis = true)
    ThemeStyle.VIOLET -> violetOled()
    ThemeStyle.CYAN -> cyanOled()
    ThemeStyle.WARM -> warmOled()
    ThemeStyle.SCOPE -> scopeOled()
}

/**
 * Material You from wallpaper. When [oledChassis] is true, keep dynamic accents
 * but force pure-black surfaces (true black / OLED).
 */
private fun resolveDynamic(context: Context, oledChassis: Boolean): ResolvedTheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = dynamicDarkColorScheme(context)
        val scheme = if (oledChassis) withOledChassis(dynamic) else dynamic
        return ResolvedTheme(
            colorScheme = scheme,
            accents = BrandAccents(
                gaugeStart = scheme.primary,
                gaugeMid = scheme.secondary,
                gaugeEnd = scheme.tertiary,
                gaugeTrack = if (oledChassis) OledGaugeTrack else scheme.surfaceVariant,
                waveform = scheme.primary,
            ),
        )
    }
    // Pre–Android 12: no wallpaper colors API — fall back to violet OLED.
    return violetOled()
}

/** Keep Material You chromatic roles; replace all surface roles with OLED blacks. */
private fun withOledChassis(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = OledBlack,
    onBackground = OledOn,
    surface = OledSurface,
    onSurface = OledOn,
    surfaceVariant = OledPanel,
    onSurfaceVariant = OledMuted,
    outline = OledOutline,
    outlineVariant = OledOutlineVariant,
    surfaceBright = OledPanelHigh,
    surfaceDim = OledBlack,
    surfaceContainer = OledPanel,
    surfaceContainerHigh = OledPanelHigh,
    surfaceContainerHighest = OledPanelHighest,
    surfaceContainerLow = OledSurface,
    surfaceContainerLowest = OledBlack,
)

private fun violetOled(): ResolvedTheme {
    val scheme = darkColorScheme(
        primary = BrandViolet,
        onPrimary = White,
        primaryContainer = VioletPrimaryContainer,
        onPrimaryContainer = VioletOnPrimaryContainer,
        secondary = BrandCyan,
        onSecondary = VioletOnSecondary,
        secondaryContainer = VioletSecondaryContainer,
        onSecondaryContainer = VioletOnSecondaryContainer,
        tertiary = BrandBlue,
        onTertiary = White,
        tertiaryContainer = VioletTertiaryContainer,
        onTertiaryContainer = VioletOnTertiaryContainer,
        background = OledBlack,
        onBackground = OledOn,
        surface = OledSurface,
        onSurface = OledOn,
        surfaceVariant = OledPanel,
        onSurfaceVariant = OledMuted,
        outline = OledOutline,
        outlineVariant = OledOutlineVariant,
    )
    return ResolvedTheme(
        colorScheme = scheme,
        accents = BrandAccents(
            gaugeStart = BrandViolet,
            gaugeMid = BrandBlue,
            gaugeEnd = BrandCyanBright,
            gaugeTrack = OledGaugeTrack,
            waveform = BrandViolet,
        ),
    )
}

private fun cyanOled(): ResolvedTheme {
    val scheme = darkColorScheme(
        primary = CyanPrimary,
        onPrimary = CyanOnPrimary,
        primaryContainer = CyanPrimaryContainer,
        onPrimaryContainer = CyanOnPrimaryContainer,
        secondary = CyanTeal,
        onSecondary = CyanOnSecondary,
        secondaryContainer = CyanSecondaryContainer,
        onSecondaryContainer = CyanOnSecondaryContainer,
        tertiary = CyanTertiary,
        onTertiary = CyanOnTertiary,
        tertiaryContainer = CyanTertiaryContainer,
        onTertiaryContainer = CyanOnTertiaryContainer,
        background = OledBlack,
        onBackground = OledOn,
        surface = OledSurface,
        onSurface = OledOn,
        surfaceVariant = OledPanel,
        onSurfaceVariant = OledMuted,
        outline = OledOutline,
        outlineVariant = OledOutlineVariant,
    )
    return ResolvedTheme(
        colorScheme = scheme,
        accents = BrandAccents(
            gaugeStart = CyanTeal,
            gaugeMid = CyanPrimary,
            gaugeEnd = CyanGaugeEnd,
            gaugeTrack = OledGaugeTrack,
            waveform = CyanPrimary,
        ),
    )
}

private fun warmOled(): ResolvedTheme {
    val scheme = darkColorScheme(
        primary = WarmAmber,
        onPrimary = WarmOnPrimary,
        primaryContainer = WarmPrimaryContainer,
        onPrimaryContainer = WarmOnPrimaryContainer,
        secondary = WarmCopper,
        onSecondary = WarmOnSecondary,
        secondaryContainer = WarmSecondaryContainer,
        onSecondaryContainer = WarmOnSecondaryContainer,
        tertiary = WarmTertiary,
        onTertiary = WarmOnTertiary,
        tertiaryContainer = WarmTertiaryContainer,
        onTertiaryContainer = WarmOnTertiaryContainer,
        background = OledBlack,
        onBackground = OledOn,
        surface = OledSurface,
        onSurface = OledOn,
        surfaceVariant = OledPanel,
        onSurfaceVariant = OledMuted,
        outline = OledOutline,
        outlineVariant = OledOutlineVariant,
    )
    return ResolvedTheme(
        colorScheme = scheme,
        accents = BrandAccents(
            gaugeStart = WarmCopper,
            gaugeMid = WarmAmber,
            gaugeEnd = WarmGaugeEnd,
            gaugeTrack = OledGaugeTrack,
            waveform = WarmAmber,
        ),
    )
}

private fun scopeOled(): ResolvedTheme {
    val scheme = darkColorScheme(
        primary = ScopeGreen,
        onPrimary = ScopeOnPrimary,
        primaryContainer = ScopePrimaryContainer,
        onPrimaryContainer = ScopeOnPrimaryContainer,
        secondary = ScopeSecondary,
        onSecondary = ScopeOnSecondary,
        secondaryContainer = ScopeSecondaryContainer,
        onSecondaryContainer = ScopeOnSecondaryContainer,
        tertiary = ScopeTertiary,
        onTertiary = OledBlack,
        tertiaryContainer = ScopeTertiaryContainer,
        onTertiaryContainer = ScopeOnTertiaryContainer,
        background = OledBlack,
        onBackground = OledOn,
        surface = OledSurface,
        onSurface = OledOn,
        surfaceVariant = OledPanel,
        onSurfaceVariant = OledMuted,
        outline = OledOutline,
        outlineVariant = OledOutlineVariant,
    )
    return ResolvedTheme(
        colorScheme = scheme,
        accents = BrandAccents(
            gaugeStart = ScopeGaugeStart,
            gaugeMid = ScopeGreen,
            gaugeEnd = ScopeGaugeEnd,
            gaugeTrack = OledGaugeTrack,
            waveform = ScopeGreen,
        ),
    )
}
