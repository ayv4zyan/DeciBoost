package com.deciboost.feature.boost.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF18FFFF),
    tertiary = Color(0xFFFF6E40),
    background = Color(0xFF0D0F14),
    surface = Color(0xFF151922),
    onPrimary = Color.White,
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5E35B1),
    secondary = Color(0xFF00ACC1),
    tertiary = Color(0xFFFF5722),
)

@Composable
fun DeciBoostTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}