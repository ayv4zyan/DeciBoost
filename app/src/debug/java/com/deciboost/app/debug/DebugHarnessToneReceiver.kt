package com.deciboost.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugHarnessToneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_START_TONE -> DebugHarnessTonePlayer.start()
            ACTION_STOP_TONE -> DebugHarnessTonePlayer.stop()
        }
    }

    companion object {
        const val ACTION_START_TONE = "com.deciboost.DEBUG.START_HARNESS_TONE"
        const val ACTION_STOP_TONE = "com.deciboost.DEBUG.STOP_HARNESS_TONE"
    }
}