package com.bell.launcher.data

import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Згорнуті сповіщення одного додатка. */
data class AppNotification(
    val packageName: String,
    val key: String,
    val count: Int,
    val title: String,
    val text: String,
    val contentIntent: PendingIntent?,
) {
    val preview: String
        get() = listOf(title, text).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * Спільне сховище активних сповіщень. Заповнюється службою
 * [com.bell.launcher.service.LauncherNotificationService], читається UI.
 *
 * Це singleton, а не частина AppContainer, бо службу створює система —
 * вона не має доступу до графа залежностей активності.
 */
object NotificationStore {

    private val _items = MutableStateFlow<Map<String, AppNotification>>(emptyMap())
    val items: StateFlow<Map<String, AppNotification>> = _items.asStateFlow()

    @Volatile
    var service: NotificationListenerService? = null

    /** Чи надано дозвіл на читання сповіщень (служба підключена). */
    val connected: Boolean get() = service != null

    fun update(list: Array<StatusBarNotification>?) {
        if (list == null) {
            _items.value = emptyMap()
            return
        }
        val grouped = HashMap<String, AppNotification>()

        list.asSequence()
            // Постійні сповіщення (плеєр, VPN, завантаження) не показуємо крапкою —
            // їх не можна прибрати, і крапка висіла б вічно.
            .filter { it.isClearable }
            .forEach { sbn ->
                val extras = sbn.notification?.extras
                val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
                val text = extras?.getCharSequence("android.text")?.toString().orEmpty()
                val existing = grouped[sbn.packageName]
                grouped[sbn.packageName] = AppNotification(
                    packageName = sbn.packageName,
                    key = sbn.key,
                    count = (existing?.count ?: 0) + 1,
                    title = existing?.title?.ifBlank { title } ?: title,
                    text = existing?.text?.ifBlank { text } ?: text,
                    contentIntent = existing?.contentIntent ?: sbn.notification?.contentIntent,
                )
            }

        _items.value = grouped
    }

    fun refreshFromService() {
        runCatching { update(service?.activeNotifications) }
    }

    /** Свайп зліва направо — відкрити сповіщення. */
    fun open(packageName: String): Boolean {
        val item = _items.value[packageName] ?: return false
        val intent = item.contentIntent ?: return false
        return runCatching { intent.send() }.isSuccess
    }

    /** Свайп справа наліво — прибрати сповіщення. */
    fun dismiss(packageName: String): Boolean {
        val item = _items.value[packageName] ?: return false
        val svc = service ?: return false
        val ok = runCatching { svc.cancelNotification(item.key) }.isSuccess
        if (ok) {
            _items.value = _items.value - packageName
        }
        return ok
    }
}
