package com.bell.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import com.bell.launcher.data.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private var callback: LauncherApps.Callback? = null

    fun start(scope: CoroutineScope) {
        scope.launch { refresh() }
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = trigger(scope)
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = trigger(scope)
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = trigger(scope)
            override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = trigger(scope)
            override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = trigger(scope)
        }
        callback = cb
        runCatching { launcherApps.registerCallback(cb) }
    }

    fun stop() {
        callback?.let { runCatching { launcherApps.unregisterCallback(it) } }
        callback = null
    }

    private fun trigger(scope: CoroutineScope) {
        scope.launch { refresh() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val list = runCatching {
            launcherApps.getActivityList(null, Process.myUserHandle()).map { info ->
                AppInfo(
                    packageName = info.componentName.packageName,
                    activityName = info.componentName.className,
                    label = info.label?.toString().orEmpty().ifBlank { info.componentName.packageName },
                    system = (info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
        }.getOrDefault(emptyList())
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
        _apps.value = list
    }

    fun find(packageName: String, activityName: String): AppInfo? =
        _apps.value.firstOrNull { it.packageName == packageName && it.activityName == activityName }
            ?: _apps.value.firstOrNull { it.packageName == packageName }

    // -------------------------------------------------------------- дії

    fun launch(packageName: String, activityName: String, sourceBounds: Rect? = null) {
        val component = ComponentName(packageName, activityName)
        val opts = Bundle()
        val ok = runCatching {
            launcherApps.startMainActivity(component, Process.myUserHandle(), sourceBounds, opts)
        }.isSuccess
        if (!ok) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent != null) runCatching { context.startActivity(intent) }
        }
    }

    fun openAppInfo(packageName: String, activityName: String) {
        val component = ComponentName(packageName, activityName)
        val ok = runCatching {
            launcherApps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
        }.isSuccess
        if (!ok) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    fun uninstall(packageName: String) {
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun canUninstall(app: AppInfo): Boolean = !app.system

    fun shortcuts(packageName: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
            runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)
}
