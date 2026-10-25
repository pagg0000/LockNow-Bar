package com.example.nowbrief

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

data class Incoming(val key: String, val app: String, val title: String, val text: String)

object NotifStore {
    val items = mutableStateListOf<Incoming>()
    fun put(n: Incoming) {
        items.removeAll { it.key == n.key }
        items.add(0, n)
        while (items.size > 20) items.removeAt(items.lastIndex)
    }
}

class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotifStore.items.removeAll { it.key == sbn.key }
    }

    private fun handle(sbn: StatusBarNotification) {
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
        NotifStore.put(Incoming(sbn.key, app, title, text))
    }
}
