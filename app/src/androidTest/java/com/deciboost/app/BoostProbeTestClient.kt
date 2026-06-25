package com.deciboost.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Minimal cross-process probe client for :app androidTest (boot receiver tests).
 * Mirrors [com.deciboost.app.test.BoostProbeClient] with correct COMPONENT_PREFIX.
 */
object BoostProbeTestClient {
    private const val ACTION_DUMP_BOOST_STATE = "com.deciboost.DEBUG.DUMP_BOOST_STATE"
    private const val DEBUG_PKG = "com.deciboost.app.debug"
    private val DEBUG_RECEIVER_COMPONENT = ComponentName(
        DEBUG_PKG,
        "$DEBUG_PKG.DebugBoostStateReceiver",
    )
    private val PROBE_URI: Uri = Uri.parse("content://$DEBUG_PKG.probe/state")

    data class Snapshot(
        val targetGainMb: Int,
        val globalEffectEnabled: Boolean,
    )

    fun dump(context: Context, timeoutMs: Long = 3000): Snapshot {
        queryViaContentProvider(context)?.let { return it }
        return dumpViaOrderedBroadcast(context, timeoutMs)
    }

    private fun queryViaContentProvider(context: Context): Snapshot? {
        return try {
            context.contentResolver.query(PROBE_URI, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                Snapshot(
                    targetGainMb = cursor.getInt(cursor.getColumnIndexOrThrow("target_gain_mb")),
                    globalEffectEnabled = cursor.getInt(
                        cursor.getColumnIndexOrThrow("global_effect_enabled"),
                    ) == 1,
                )
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun dumpViaOrderedBroadcast(context: Context, timeoutMs: Long): Snapshot {
        val intent = Intent(ACTION_DUMP_BOOST_STATE).setComponent(DEBUG_RECEIVER_COMPONENT)
        val latch = CountDownLatch(1)
        var snapshot: Snapshot? = null
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val extras = getResultExtras(true)
                snapshot = Snapshot(
                    targetGainMb = extras.getInt("target_gain_mb"),
                    globalEffectEnabled = extras.getBoolean("global_effect_enabled"),
                )
                latch.countDown()
            }
        }
        context.sendOrderedBroadcast(
            intent,
            null,
            resultReceiver,
            Handler(Looper.getMainLooper()),
            Activity.RESULT_OK,
            null,
            null,
        )
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return snapshot ?: error("Probe dump timed out; check logcat tag DeciBoostProbe")
    }
}
