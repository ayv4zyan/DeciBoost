package com.deciboost.app.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import dagger.hilt.android.EntryPointAccessors

/**
 * Cross-process probe channel for instrumentation tests (more reliable than ordered
 * broadcast on API 36+ emulators).
 */
class BoostProbeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.path != "/state") return null
        val probe = EntryPointAccessors.fromApplication(
            requireContext().applicationContext,
            DebugProbeEntryPoint::class.java,
        ).boostEngineProbe()

        val targetGainMb = probe.getGlobalTargetGainMilliBel()
        val enabled = probe.isGlobalEffectEnabled()
        val lastReapply = probe.getLastReapplyReason().name
        val rmsRatio = probe.measureRmsRatio()

        Log.i(
            DebugBoostStateReceiver.TAG,
            "delivered=true target_gain_mb=$targetGainMb global_effect_enabled=$enabled " +
                "last_reapply=$lastReapply rms_ratio=$rmsRatio",
        )

        return MatrixCursor(
            arrayOf(
                "delivered",
                "target_gain_mb",
                "global_effect_enabled",
                "last_reapply",
                "rms_ratio",
            ),
        ).apply {
            addRow(
                arrayOf(
                    1,
                    targetGainMb,
                    if (enabled) 1 else 0,
                    lastReapply,
                    rmsRatio,
                ),
            )
        }
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.deciboost.probe"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.deciboost.app.debug.probe"
        val STATE_URI: Uri = Uri.parse("content://$AUTHORITY/state")
    }
}
