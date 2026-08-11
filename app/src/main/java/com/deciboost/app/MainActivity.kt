package com.deciboost.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deciboost.app.navigation.DeciBoostNavHost
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.data.ThemeStyle
import com.deciboost.feature.boost.ui.theme.DeciBoostTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: BoostPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeStyle by preferences.themeStyle.collectAsStateWithLifecycle(
                initialValue = ThemeStyle.DEFAULT,
            )

            DeciBoostTheme(themeStyle = themeStyle) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DeciBoostNavHost()
                }
            }
        }
    }
}
