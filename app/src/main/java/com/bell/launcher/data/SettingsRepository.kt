package com.bell.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GestureAction(val title: String) {
    NONE("Нічого"),
    OPEN_DRAWER("Відкрити шухляду"),
    EXPAND_NOTIFICATIONS("Шторка сповіщень"),
    OPEN_SETTINGS("Налаштування лаунчера"),
    OPEN_THEMES("Галерея тем"),
    OPEN_WIDGETS("Додати віджет"),
}

data class LauncherSettings(
    val themeId: String? = null,
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.EXPAND_NOTIFICATIONS,
    val twoFingerSwipeDown: GestureAction = GestureAction.OPEN_SETTINGS,
    val iconScale: Float = 1f,
    val showIconsOverride: Boolean? = null,
    val hiddenApps: Set<String> = emptySet(),
    // погода
    val weatherEnabled: Boolean = true,
    val useLocation: Boolean = true,
    val manualCity: String = "",
    val manualLat: Double? = null,
    val manualLon: Double? = null,
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("launcher_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private fun read(): LauncherSettings = LauncherSettings(
        themeId = prefs.getString(KEY_THEME, null),
        swipeUp = gesture(KEY_SWIPE_UP, GestureAction.OPEN_DRAWER),
        swipeDown = gesture(KEY_SWIPE_DOWN, GestureAction.EXPAND_NOTIFICATIONS),
        twoFingerSwipeDown = gesture(KEY_TWO_FINGER, GestureAction.OPEN_SETTINGS),
        iconScale = prefs.getFloat(KEY_ICON_SCALE, 1f),
        showIconsOverride = if (prefs.contains(KEY_ICONS)) prefs.getBoolean(KEY_ICONS, true) else null,
        hiddenApps = prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet(),
        weatherEnabled = prefs.getBoolean(KEY_WEATHER, true),
        useLocation = prefs.getBoolean(KEY_USE_LOCATION, true),
        manualCity = prefs.getString(KEY_CITY, "").orEmpty(),
        manualLat = if (prefs.contains(KEY_LAT)) prefs.getFloat(KEY_LAT, 0f).toDouble() else null,
        manualLon = if (prefs.contains(KEY_LON)) prefs.getFloat(KEY_LON, 0f).toDouble() else null,
    )

    private fun gesture(key: String, default: GestureAction): GestureAction =
        runCatching { GestureAction.valueOf(prefs.getString(key, default.name)!!) }.getOrDefault(default)

    private fun mutate(block: (LauncherSettings) -> LauncherSettings) {
        val next = block(_settings.value)
        prefs.edit().apply {
            putString(KEY_THEME, next.themeId)
            putString(KEY_SWIPE_UP, next.swipeUp.name)
            putString(KEY_SWIPE_DOWN, next.swipeDown.name)
            putString(KEY_TWO_FINGER, next.twoFingerSwipeDown.name)
            putFloat(KEY_ICON_SCALE, next.iconScale)
            if (next.showIconsOverride == null) remove(KEY_ICONS) else putBoolean(KEY_ICONS, next.showIconsOverride)
            putStringSet(KEY_HIDDEN, next.hiddenApps)
            putBoolean(KEY_WEATHER, next.weatherEnabled)
            putBoolean(KEY_USE_LOCATION, next.useLocation)
            putString(KEY_CITY, next.manualCity)
            if (next.manualLat == null) remove(KEY_LAT) else putFloat(KEY_LAT, next.manualLat.toFloat())
            if (next.manualLon == null) remove(KEY_LON) else putFloat(KEY_LON, next.manualLon.toFloat())
        }.apply()
        _settings.value = next
    }

    fun setTheme(id: String) = mutate { it.copy(themeId = id) }

    fun setGesture(slot: GestureSlot, action: GestureAction) = mutate {
        when (slot) {
            GestureSlot.SWIPE_UP -> it.copy(swipeUp = action)
            GestureSlot.SWIPE_DOWN -> it.copy(swipeDown = action)
            GestureSlot.TWO_FINGER_SWIPE_DOWN -> it.copy(twoFingerSwipeDown = action)
        }
    }

    fun setIconScale(scale: Float) = mutate { it.copy(iconScale = scale.coerceIn(0.7f, 1.5f)) }
    fun setIconsOverride(value: Boolean?) = mutate { it.copy(showIconsOverride = value) }
    fun toggleHidden(key: String) = mutate {
        it.copy(hiddenApps = if (key in it.hiddenApps) it.hiddenApps - key else it.hiddenApps + key)
    }

    fun setWeatherEnabled(value: Boolean) = mutate { it.copy(weatherEnabled = value) }
    fun setUseLocation(value: Boolean) = mutate { it.copy(useLocation = value) }
    fun setManualPlace(city: String, lat: Double?, lon: Double?) = mutate {
        it.copy(manualCity = city, manualLat = lat, manualLon = lon)
    }

    companion object {
        private const val KEY_THEME = "theme_id"
        private const val KEY_SWIPE_UP = "swipe_up"
        private const val KEY_SWIPE_DOWN = "swipe_down"
        private const val KEY_TWO_FINGER = "two_finger_down"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_ICONS = "show_icons"
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_WEATHER = "weather_enabled"
        private const val KEY_USE_LOCATION = "weather_use_location"
        private const val KEY_CITY = "weather_city"
        private const val KEY_LAT = "weather_lat"
        private const val KEY_LON = "weather_lon"
    }
}

enum class GestureSlot(val title: String) {
    SWIPE_UP("Свайп вгору"),
    SWIPE_DOWN("Свайп вниз"),
    TWO_FINGER_SWIPE_DOWN("Свайп вниз двома пальцями"),
}
