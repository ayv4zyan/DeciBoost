package com.deciboost.feature.boost.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Non-Material accent tokens used by the boost gauge and waveform.
 * Provided by [DeciBoostTheme] so theme styles recolor custom chrome.
 */
data class BrandAccents(
    val gaugeStart: Color,
    val gaugeMid: Color,
    val gaugeEnd: Color,
    val gaugeTrack: Color,
    val waveform: Color,
)

val DefaultBrandAccents = BrandAccents(
    gaugeStart = BrandViolet,
    gaugeMid = BrandBlue,
    gaugeEnd = BrandCyanBright,
    gaugeTrack = Color(0xFF1E1E1E),
    waveform = BrandViolet,
)

val LocalBrandAccents = staticCompositionLocalOf { DefaultBrandAccents }
