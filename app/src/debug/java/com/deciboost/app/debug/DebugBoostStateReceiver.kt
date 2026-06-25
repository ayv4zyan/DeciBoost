package com.deciboost.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors

class DebugBoostStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DUMP_BOOST_STATE) return

        val probe = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugProbeEntryPoint::class.java,
        ).boostEngineProbe()

        val targetGainMb = probe.getGlobalTargetGainMilliBel()
        val enabled = probe.isGlobalEffectEnabled()
        val lastReapply = probe.getLastReapplyReason().name
        val rmsRatio = probe.measureRmsRatio()

        val result = goAsync()
        val extras = android.os.Bundle().apply {
            putBoolean("delivered", true)
            putInt("target_gain_mb", targetGainMb)
            putBoolean("global_effect_enabled", enabled)
            putString("last_reapply", lastReapply)
            putFloat("rms_ratio", rmsRatio)
        }
        result.setResultExtras(extras)

        Log.i(
            TAG,
            "delivered=true target_gain_mb=$targetGainMb global_effect_enabled=$enabled " +
                "last_reapply=$lastReapply rms_ratio=$rmsRatio",
        )
        result.finish()
    }

    companion object {
        const val ACTION_DUMP_BOOST_STATE = "com.deciboost.DEBUG.DUMP_BOOST_STATE"
        const val TAG = "DeciBoostProbe"
    }
}
