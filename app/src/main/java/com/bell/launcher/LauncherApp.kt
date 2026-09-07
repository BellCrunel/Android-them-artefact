package com.bell.launcher

import android.app.Application
import android.content.Context
import com.bell.launcher.data.AppRepository
import com.bell.launcher.data.LayoutRepository
import com.bell.launcher.data.SettingsRepository
import com.bell.launcher.data.WeatherRepository
import com.bell.launcher.theme.IconLoader
import com.bell.launcher.theme.ThemeRepository

class AppContainer(context: Context) {
    val appRepository = AppRepository(context)
    val layoutRepository = LayoutRepository(context)
    val settingsRepository = SettingsRepository(context)
    val themeRepository = ThemeRepository(context)
    val weatherRepository = WeatherRepository(context)
    val iconLoader = IconLoader(context)
}

class LauncherApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as LauncherApp).container
