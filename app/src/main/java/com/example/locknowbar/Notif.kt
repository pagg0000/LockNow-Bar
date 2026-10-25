package com.example.locknowbar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat

data class Notif(val key: String, val app: String, val title: String, val text: String)

object NotifStore {
    val items = mutableStateListOf<Notif>()
    fun add(n: Notif) {
        items.removeAll { it.key == n.key }
        items.add(0, n)
        if (items.size > 10) items.removeAt(items.lastIndex)
    }
}

class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val app = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        NotifStore.add(Notif(sbn.key, app, title, text))
        showOnLockScreen(app, title)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotifStore.items.removeAll { it.key == sbn.key }
    }

    private fun showOnLockScreen(app: String, title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "locknowbar"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Lock NowBar", NotificationManager.IMPORTANCE_HIGH)
        )
        val intent = Intent(this, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(app)
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(8000)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        nm.notify(1, notif)
    }
}
