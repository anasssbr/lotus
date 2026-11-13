package com.dn0ne.player.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dn0ne.player.R

/** Progress + completion notifications for the online download screen. */
class DownloadNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private var lastPercent = -1

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.download_notification_channel_description)
                setShowBadge(false)
            }

            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun showProgress(title: String, progress: Float) {
        if (!canPostNotifications()) return

        // The download loop reports every 64 KB; redraw the notification only on whole percent.
        val percent = progress.toInt().coerceIn(0, 100)
        if (percent == lastPercent) return
        lastPercent = percent

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.download_in_progress))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showComplete(title: String) {
        lastPercent = -1

        if (!canPostNotifications()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.download_complete))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        lastPercent = -1
        manager.cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 4211
    }
}
