package com.moqaddas.handsoff.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.moqaddas.handsoff.presentation.MainActivity

object GuardianNotification {

    const val CHANNEL_ID       = "handsoff_guardian"
    const val ALERT_CHANNEL_ID = "handsoff_alerts"
    const val NOTIF_ID         = 1
    const val SENSOR_NOTIF_ID  = 2

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Persistent silent status channel
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "HandsOff Guardian", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent privacy guardian status"
                setShowBadge(false)
            }
        )

        // High-priority alert channel for sensor access events
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Privacy Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Mic and camera access alerts"
                setShowBadge(true)
            }
        )
    }

    fun buildSensorAlert(context: Context, appName: String, sensorType: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ $sensorType Access Detected")
            .setContentText("$appName is using your $sensorType")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun buildPermissionAlert(context: Context, appName: String, newPerms: Set<String>): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val summary = newPerms.joinToString(", ") { it.substringAfterLast('.') }
        return NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("⚠️ New Permissions Detected")
            .setContentText("$appName gained: $summary")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun build(context: Context, blockedCount: Int): Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val body = if (blockedCount == 0) "Monitoring your privacy"
                   else "$blockedCount tracker${if (blockedCount == 1) "" else "s"} blocked"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("HandsOff is active")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
