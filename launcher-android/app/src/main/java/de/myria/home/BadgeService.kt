package de.myria.home

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Zählt Benachrichtigungen pro App → Punkte auf den Icons. */
class BadgeService : NotificationListenerService() {

    companion object {
        @Volatile var counts: Map<String, Int> = emptyMap()
            private set
        var onChange: (() -> Unit)? = null
        private val main = Handler(Looper.getMainLooper())

        fun granted(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, BadgeService::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }

    private fun recount() {
        val list = try { activeNotifications } catch (_: Exception) { null } ?: return
        val m = HashMap<String, Int>()
        for (sbn in list) {
            val n = sbn.notification ?: continue
            if (sbn.isOngoing) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            val num = if (n.number > 0) n.number else 1
            m[sbn.packageName] = (m[sbn.packageName] ?: 0) + num
        }
        counts = m
        main.post { onChange?.invoke() }
    }

    override fun onListenerConnected() = recount()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()
    override fun onListenerDisconnected() {
        counts = emptyMap()
        main.post { onChange?.invoke() }
    }
}
