package com.bell.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bell.launcher.AppContainer
import com.bell.launcher.data.BackgroundMode
import com.bell.launcher.data.FontChoice
import com.bell.launcher.data.GestureAction
import com.bell.launcher.data.GestureSlot
import com.bell.launcher.data.IconStyle
import com.bell.launcher.data.AppNotification
import com.bell.launcher.data.LauncherSettings
import com.bell.launcher.data.NotificationStore
import com.bell.launcher.data.WallpaperItem
import com.bell.launcher.data.Weather
import com.bell.launcher.data.WeatherStatus
import com.bell.launcher.service.LauncherNotificationService
import com.bell.launcher.data.model.AppEntry
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.data.model.AppRef
import com.bell.launcher.data.model.HomeEntry
import com.bell.launcher.data.model.HomeLayout
import com.bell.launcher.data.model.WidgetEntry
import com.bell.launcher.theme.Appearance
import com.bell.launcher.theme.IconPackLoader
import com.bell.launcher.theme.LauncherThemeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Overlay { NONE, DRAWER, SETTINGS, APPEARANCE, HIDDEN_APPS, FAVORITES }

data class LauncherUiState(
    val apps: List<AppInfo> = emptyList(),
    val layout: HomeLayout = HomeLayout(),
    val settings: LauncherSettings = LauncherSettings(),
    val theme: LauncherThemeData? = null,
    val themes: List<LauncherThemeData> = emptyList(),
) {
    /** Ключі додатків, що зараз є на головному екрані. */
    val favoriteKeys: Set<String>
        get() = layout.entries.filterIsInstance<AppEntry>().map { it.app.key }.toSet()
}

class LauncherViewModel(private val container: AppContainer) : ViewModel() {

    private val _overlay = MutableStateFlow(Overlay.NONE)
    val overlay: StateFlow<Overlay> = _overlay

    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolderId: StateFlow<String?> = _openFolderId

    val weather: StateFlow<Weather?> = container.weatherRepository.weather
    val weatherStatus: StateFlow<WeatherStatus> = container.weatherRepository.status

    /** Опис стану погоди для рядка в налаштуваннях. */
    fun weatherText(): String = container.weatherRepository.statusText()

    /** Активні сповіщення, згруповані за пакетом додатка. */
    val notifications: StateFlow<Map<String, AppNotification>> = NotificationStore.items

    fun openNotification(packageName: String) = NotificationStore.open(packageName)
    fun dismissNotification(packageName: String) = NotificationStore.dismiss(packageName)
    fun notificationsEnabled(): Boolean = LauncherNotificationService.isEnabled(container.context)

    val state: StateFlow<LauncherUiState> = combine(
        container.appRepository.apps,
        container.layoutRepository.layout,
        container.settingsRepository.settings,
        container.themeRepository.themes,
    ) { apps, layout, settings, themes ->
        val theme = themes.firstOrNull { it.id == settings.themeId }
            ?: themes.firstOrNull { it.id == com.bell.launcher.theme.ThemeRepository.BUILTIN_DEFAULT }
            ?: themes.firstOrNull()
        container.iconLoader.configure(theme, Appearance.iconSpec(theme, settings))
        LauncherUiState(apps, layout, settings, theme, themes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherUiState())

    val iconLoader get() = container.iconLoader
    val appRepository get() = container.appRepository

    /** Встановлені на пристрої icon pack (ADW/Nova), для списку в налаштуваннях. */
    fun installedIconPacks(): List<Pair<String, String>> =
        runCatching { IconPackLoader.installedPacks(container.context) }.getOrDefault(emptyList())

    init {
        container.appRepository.start(viewModelScope)
        viewModelScope.launch {
            container.appRepository.apps.collect { apps ->
                if (apps.isNotEmpty() && container.layoutRepository.isEmpty) {
                    container.layoutRepository.seedIfEmpty(viewModelScope, apps)
                }
            }
        }
        // Погода: повторюємо спроби, поки не вийде. Раніше був один-єдиний
        // запит при старті — якщо він не знаходив координат, погода не
        // з'являлася вже ніколи й без жодного повідомлення.
        viewModelScope.launch {
            while (true) {
                container.weatherRepository.refresh(container.settingsRepository.settings.value)
                val ok = container.weatherRepository.weather.value != null
                delay(if (ok) 15 * 60_000L else 60_000L)
            }
        }
    }

    /** Пошук паків іконок у Play Market — своїх ми не постачаємо. */
    fun openIconPackSearch() {
        val context = container.context
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps")),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=icon%20pack&c=apps"),
            ),
        )
        for (intent in intents) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return
        }
    }

    override fun onCleared() {
        container.appRepository.stop()
        super.onCleared()
    }

    // ------------------------------------------------------------- навігація

    fun openOverlay(value: Overlay) { _overlay.value = value }
    fun closeOverlay() { _overlay.value = Overlay.NONE }
    fun openFolder(id: String?) { _openFolderId.value = id }

    // ---------------------------------------------------------- домашній список

    fun addAppToHome(app: AppInfo) = container.layoutRepository.addApp(viewModelScope, app)
    fun toggleFavorite(app: AppInfo) = container.layoutRepository.toggleApp(viewModelScope, app)
    fun removeEntry(id: String) = container.layoutRepository.remove(viewModelScope, id)
    fun moveEntry(from: Int, to: Int) = container.layoutRepository.move(viewModelScope, from, to)
    fun renameEntry(id: String, label: String) = container.layoutRepository.rename(viewModelScope, id, label)
    fun mergeIntoFolder(targetId: String, sourceId: String) =
        container.layoutRepository.mergeIntoFolder(viewModelScope, targetId, sourceId)
    fun removeFromFolder(folderId: String, ref: AppRef) =
        container.layoutRepository.removeFromFolder(viewModelScope, folderId, ref)
    fun addToFolder(folderId: String, ref: AppRef) =
        container.layoutRepository.addToFolder(viewModelScope, folderId, ref)
    fun makeFolder(entryId: String, name: String = "Папка") =
        container.layoutRepository.convertToFolder(viewModelScope, entryId, name)

    fun addWidget(appWidgetId: Int, heightDp: Int, provider: String) =
        container.layoutRepository.add(
            viewModelScope,
            WidgetEntry(appWidgetId = appWidgetId, heightDp = heightDp, providerPackage = provider),
        )

    fun entryById(id: String?): HomeEntry? = state.value.layout.entries.firstOrNull { it.id == id }

    // ----------------------------------------------------------- налаштування

    fun setTheme(id: String) = container.settingsRepository.setTheme(id)
    fun setGesture(slot: GestureSlot, action: GestureAction) =
        container.settingsRepository.setGesture(slot, action)
    fun setIconScale(value: Float) = container.settingsRepository.setIconScale(value)
    fun setIconsOverride(value: Boolean?) = container.settingsRepository.setIconsOverride(value)
    fun toggleHidden(key: String) = container.settingsRepository.toggleHidden(key)

    fun setIconStyle(style: IconStyle) = container.settingsRepository.setIconStyle(style)
    fun setIconPack(pkg: String?) = container.settingsRepository.setIconPack(pkg)
    fun setBackgroundMode(mode: BackgroundMode) = container.settingsRepository.setBackgroundMode(mode)
    fun setWallpaper(file: String?) = container.settingsRepository.setWallpaper(file)
    fun setWallpaperDim(value: Float) = container.settingsRepository.setWallpaperDim(value)

    /** Зображення з assets/wallpapers. */
    fun wallpapers(): List<WallpaperItem> = container.wallpaperRepository.list()
    fun loadWallpaper(fileName: String, maxWidth: Int = 1440) =
        container.wallpaperRepository.load(fileName, maxWidth)
    fun setFont(font: FontChoice) = container.settingsRepository.setFont(font)
    fun setClockSeparator(value: String?) = container.settingsRepository.setClockSeparator(value)

    fun setWeatherEnabled(value: Boolean) {
        container.settingsRepository.setWeatherEnabled(value)
        if (value) refreshWeather(force = true)
    }

    fun setUseLocation(value: Boolean) {
        container.settingsRepository.setUseLocation(value)
        refreshWeather(force = true)
    }

    fun setCity(city: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (city.isBlank()) {
                container.settingsRepository.setManualPlace("", null, null)
                onDone(true)
                return@launch
            }
            val point = container.weatherRepository.geocode(city)
            if (point == null) {
                container.settingsRepository.setManualPlace(city, null, null)
                onDone(false)
            } else {
                container.settingsRepository.setManualPlace(point.third, point.first, point.second)
                refreshWeather(force = true)
                onDone(true)
            }
        }
    }

    fun refreshWeather(force: Boolean = false) {
        viewModelScope.launch {
            container.weatherRepository.refresh(container.settingsRepository.settings.value, force)
        }
    }

    fun hasLocationPermission(): Boolean = container.weatherRepository.hasLocationPermission()

    // ------------------------------------------------------------------ теми

    fun installTheme(uri: android.net.Uri, onResult: (Result<LauncherThemeData>) -> Unit) {
        viewModelScope.launch {
            val result = container.themeRepository.install(uri)
            result.getOrNull()?.let { setTheme(it.id) }
            onResult(result)
        }
    }

    fun deleteTheme(id: String) {
        if (container.themeRepository.delete(id)) {
            if (state.value.settings.themeId == id) {
                setTheme(com.bell.launcher.theme.ThemeRepository.BUILTIN_DEFAULT)
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LauncherViewModel(container) as T
    }
}
