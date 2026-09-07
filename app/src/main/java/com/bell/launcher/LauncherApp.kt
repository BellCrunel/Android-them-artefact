package com.bell.launcher

import android.app.Application
import android.content.Context
import com.bell.launcher.data.AppRepository
import com.bell.launcher.data.LayoutRepository
import com.bell.launcher.data.SettingsRepository
import com.bell.launcher.data.WeatherRepository
import com.bell.launcher.theme.IconLoader
import com.bell.launcher.theme.ThemeRepository

class AppContainer(val context: Context) {
    val appRepository = AppRepository(context).also { CrashLog.step(context, "AppRepository ok") }
    val layoutRepository = LayoutRepository(context).also { CrashLog.step(context, "LayoutRepository ok") }
    val settingsRepository = SettingsRepository(context).also { CrashLog.step(context, "SettingsRepository ok") }
    val themeRepository = ThemeRepository(context).also { CrashLog.step(context, "ThemeRepository ok") }
    val weatherRepository = WeatherRepository(context).also { CrashLog.step(context, "WeatherRepository ok") }
    val iconLoader = IconLoader(context).also { CrashLog.step(context, "IconLoader ok") }
}

class LauncherApp : Application() {

    private var _container: AppContainer? = null

    /** true, якщо ініціалізація пройшла успішно і лаунчер можна показувати. */
    val isReady: Boolean get() = _container != null

    val container: AppContainer
        get() = _container ?: error("AppContainer не ініціалізовано")

    override fun onCreate() {
        super.onCreate()
        // Найперше — щоб перехопити навіть падіння під час ініціалізації.
        CrashLog.install(this)
        CrashLog.beginRun(this)
        try {
            CrashLog.step(this, "AppContainer: створення")
            _container = AppContainer(this)
            CrashLog.step(this, "AppContainer: готово")
        } catch (t: Throwable) {
            CrashLog.record(this, t)
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as LauncherApp).container
