package com.kal.hereme.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.kal.hereme.R
import com.kal.hereme.core.Money
import com.kal.hereme.core.Paise
import com.kal.hereme.ui.MainActivity

object Notifications {

    const val CHANNEL_BUBBLE = "bubble"
    const val CHANNEL_CHECK_IN = "check_in"

    const val BUBBLE_NOTIFICATION_ID = 1
    const val CHECK_IN_NOTIFICATION_ID = 2

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BUBBLE,
                context.getString(R.string.bubble_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.bubble_channel_description)
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHECK_IN,
                context.getString(R.string.checkin_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.checkin_channel_description)
            },
        )
    }

    /** Deliberately silent and badge-less — this only exists to keep the bubble alive. */
    fun bubbleNotification(context: Context): Notification =
        Notification.Builder(context, CHANNEL_BUBBLE)
            .setContentTitle(context.getString(R.string.bubble_notification_title))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .build()

    /**
     * The daily honesty check. A tracked balance nobody confirms is a number that quietly
     * stops being true, and a tracker you have learned to ignore is worse than none.
     */
    fun postCheckIn(context: Context, balance: Paise) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val confirm = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CheckInReceiver::class.java).setAction(CheckInReceiver.ACTION_CONFIRM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL_CHECK_IN)
            .setContentTitle("Still ${Money.format(balance)}?")
            .setContentText("Confirm it, or tap to put in the real number.")
            .setSmallIcon(R.drawable.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                Notification.Action.Builder(
                    null as Icon?,
                    "Yes, that's right",
                    confirm,
                ).build(),
            )
            .build()

        manager.notify(CHECK_IN_NOTIFICATION_ID, notification)
    }

    fun cancelCheckIn(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(CHECK_IN_NOTIFICATION_ID)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
