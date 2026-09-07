package com.bell.launcher.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.staticCompositionLocalOf

const val WIDGET_HOST_ID = 4242

val LocalWidgetController = staticCompositionLocalOf<WidgetController> {
    error("WidgetController не наданий")
}

/**
 * Обгортка над AppWidgetHost: вибір, конфігурація, створення View та видалення віджетів.
 */
class WidgetController(private val activity: ComponentActivity) {

    val host: AppWidgetHost = AppWidgetHost(activity, WIDGET_HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)

    private var onReady: ((Int, AppWidgetProviderInfo) -> Unit)? = null
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val configureLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingWidgetId
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (result.resultCode == Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                manager.getAppWidgetInfo(id)?.let { onReady?.invoke(id, it) }
            } else if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                host.deleteAppWidgetId(id)
            }
        }

    private val pickLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            if (result.resultCode != Activity.RESULT_OK || id == AppWidgetManager.INVALID_APPWIDGET_ID) {
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(id)
                return@registerForActivityResult
            }

            val info = manager.getAppWidgetInfo(id)
            if (info == null) {
                host.deleteAppWidgetId(id)
                return@registerForActivityResult
            }

            if (info.configure != null) {
                pendingWidgetId = id
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                runCatching { configureLauncher.launch(intent) }
                    .onFailure { onReady?.invoke(id, info) }
            } else {
                onReady?.invoke(id, info)
            }
        }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    /** Відкриває системний діалог вибору віджета. */
    fun pickWidget(onWidgetReady: (Int, AppWidgetProviderInfo) -> Unit) {
        onReady = onWidgetReady
        val id = host.allocateAppWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<AppWidgetProviderInfo>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Bundle>())
        }
        runCatching { pickLauncher.launch(intent) }.onFailure { host.deleteAppWidgetId(id) }
    }

    fun createView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }.getOrNull()
    }

    fun delete(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    fun infoOf(appWidgetId: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(appWidgetId)
}
