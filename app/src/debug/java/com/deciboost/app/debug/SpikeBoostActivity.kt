package com.deciboost.app.debug

import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deciboost.app.service.BoostServiceClient
import com.deciboost.core.data.BoostPreferences
import com.deciboost.core.domain.BoostController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug spike activity for manual YouTube pause/resume validation.
 * Logs playback callback events to logcat tag DeciBoostSpike.
 *
 * Intentionally seeds onboarding and routes through [BoostServiceClient] so
 * manual spike sessions match production FGS lifecycle (debug-only bypass).
 */
@AndroidEntryPoint
class SpikeBoostActivity : ComponentActivity() {

    @Inject lateinit var controller: BoostController
    @Inject lateinit var preferences: BoostPreferences
    @Inject lateinit var serviceClient: BoostServiceClient

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }
    private val spikeHandler = Handler(Looper.getMainLooper())
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            if (configs.isEmpty()) {
                Log.i(TAG, "playbackConfigChanged: count=0")
                return
            }
            configs.forEachIndexed { index, config ->
                val attrs = config.audioAttributes
                Log.i(
                    TAG,
                    "playbackConfigChanged[$index]: usage=${attrs.usage} " +
                        "contentType=${attrs.contentType} configs=${configs.size}",
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager.registerAudioPlaybackCallback(playbackCallback, spikeHandler)
        Log.i(TAG, "Spike activity started — monitor logcat for playback events")

        setContent {
            MaterialTheme {
                var slider by remember { mutableFloatStateOf(150f) }
                val scope = rememberCoroutineScope()
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Spike Boost: ${slider.toInt()}%")
                    Slider(
                        value = slider,
                        onValueChange = { slider = it },
                        valueRange = 100f..200f,
                    )
                    Button(onClick = {
                        scope.launch {
                            preferences.setOnboardingComplete(true)
                            serviceClient.ensureRunning().getOrThrow()
                            controller.setBoostPercent(slider.toInt())
                            Log.i(TAG, "Applied boost ${slider.toInt()}%")
                        }
                    }) {
                        Text("Apply Boost")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onDestroy()
    }

    companion object {
        const val TAG = "DeciBoostSpike"
    }
}