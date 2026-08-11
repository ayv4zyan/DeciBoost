package com.deciboost.feature.boost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.deciboost.core.data.ThemeStyle

@Composable
fun DeciBoostTheme(
    themeStyle: ThemeStyle = ThemeStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Re-resolve when wallpaper/system colors change (Material You).
    val configuration = LocalConfiguration.current
    val resolved = remember(themeStyle, configuration) {
        resolveTheme(context, themeStyle)
    }

    CompositionLocalProvider(LocalBrandAccents provides resolved.accents) {
        MaterialTheme(
            colorScheme = resolved.colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
