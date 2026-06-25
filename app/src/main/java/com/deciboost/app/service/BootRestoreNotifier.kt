package com.deciboost.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.deciboost.app.MainActivity
import com.deciboost.app.R

object BootRestoreNotifier {

    const val BOOT_NOTIFICATION_ID = 2001
    private const val CHANNEL_ID = "deciboost_boot_restore"
    private const val TAG = "BootRestoreNotifier"
    private val channelCreatedContexts = mutableSetOf<String>()

    fun showRestoreNotification(context: Context, savedBoost: Int) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skipping boot restore notification")
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureNotificationChannel(context, manager)

        val restoreIntent = Intent(context, BootRestoreTrampolineActivity::class.java).apply {
            action = BootRestoreActionReceiver.ACTION_RESTORE_BOOST
            putExtra(BootRestoreActionReceiver.EXTRA_BOOST, savedBoost)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            savedBoost,
            restoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_notification_title))
            .setContentText(context.getString(R.string.boot_notification_text, savedBoost))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.boot_notification_restore),
                pendingIntent,
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(BOOT_NOTIFICATION_ID, notification)
    }

    fun showFgsBlockedNotification(context: Context, savedBoost: Int) {
        if (!canPostNotifications(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; cannot prompt user to open app")
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        ensureNotificationChannel(context, manager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            savedBoost + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_notification_fgs_blocked_title))
            .setContentText(context.getString(R.string.boot_notification_fgs_blocked_text, savedBoost))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.boot_notification_open_app),
                pendingIntent,
            )
            .setAutoCancel(true)
            .build()

        manager.notify(BOOT_NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context, manager: NotificationManager) {
        val cacheKey = context.packageName
        if (channelCreatedContexts.contains(cacheKey)) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.boot_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
        channelCreatedContexts.add(cacheKey)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}