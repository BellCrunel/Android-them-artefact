package com.bell.launcher.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bell.launcher.data.NotificationStore

/**
 * Читає активні сповіщення, щоб показувати крапку біля додатка
 * і давати змогу відкрити/прибрати сповіщення свайпом.
 *
 * Працює лише після того, як користувач увімкне доступ у системних налаштуваннях
 * (Налаштування → Доступ до сповіщень). Без дозволу система просто не запускає службу.
 */
class LauncherNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.service = this
        NotificationStore.refreshFromService()
    }

    override fun onListenerDisconnected() {
        NotificationStore.service = null
        NotificationStore.update(null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        NotificationStore.refreshFromService()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotificationStore.refreshFromService()
    }

    companion object {
        /** Чи ввімкнено доступ до сповіщень у системних налаштуваннях. */
        fun isEnabled(context: Context): Boolean = runCatching {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val component = ComponentName(context, LauncherNotificationService::class.java)
            flat.split(":").any {
                val parsed = ComponentName.unflattenFromString(it)
                parsed != null && parsed.packageName == component.packageName
            }
        }.getOrDefault(false)
    }
}
