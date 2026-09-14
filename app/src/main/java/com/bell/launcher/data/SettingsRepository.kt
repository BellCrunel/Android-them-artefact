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
    OPEN_THEMES("Вигляд і теми"),
    OPEN_FAVORITES("Обрані додатки"),
    OPEN_WIDGETS("Додати віджет"),
}

/**
 * Готові стилі іконок, які перекривають те, що задано в темі.
 * Це «вбудовані паки»: вони працюють з будь-яким набором додатків,
 * бо перемальовують системну іконку, а не підміняють її картинкою.
 */
enum class IconStyle(val title: String) {
    THEME("Як у темі"),
    ORIGINAL("Без обробки"),
    CIRCLE("Круглі"),
    SQUIRCLE("Squircle"),
    TILE("Плитки"),
    PEBBLE("Камінці"),
    GLASS("Скло"),
    MONO("Монохром"),
}

/** Що малювати позаду списку. */
enum class BackgroundMode(val title: String) {
    SYSTEM("Системні шпалери"),
    THEME("Фон із теми"),
    IMAGE("Зображення з папки wallpapers"),
}

/** Шрифти, доступні на будь-якому Android без завантаження. */
enum class FontChoice(val title: String, val deviceName: String?) {
    THEME("Як у темі", null),
    SANS("Системний", "sans-serif"),
    CONDENSED("Вузький", "sans-serif-condensed"),
    LIGHT("Тонкий", "sans-serif-light"),
    MEDIUM("Напівжирний", "sans-serif-medium"),
    BLACK("Жирний", "sans-serif-black"),
    SERIF("Serif", "serif"),
    MONOSPACE("Моноширинний", "monospace"),
    CURSIVE("Рукописний", "cursive"),
}

/** Порядок рядків на головному екрані. */
enum class HomeOrder(val title: String) {
    MANUAL("Вручну"),
    FREQUENCY("За частотою"),
    ALPHABET("За абеткою"),
}

data class LauncherSettings(
    val themeId: String? = null,
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.EXPAND_NOTIFICATIONS,
    val twoFingerSwipeDown: GestureAction = GestureAction.OPEN_SETTINGS,
    val iconScale: Float = 1f,
    val showIconsOverride: Boolean? = null,
    /** Показувати назви додатків на головному екрані (у шухляді вони завжди є). */
    val showLabels: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    val homeOrder: HomeOrder = HomeOrder.MANUAL,
    // вигляд
    val iconStyle: IconStyle = IconStyle.THEME,
    val iconPackPackage: String? = null,
    val backgroundMode: BackgroundMode = BackgroundMode.SYSTEM,
    /** Ім'я файлу з assets/wallpapers, коли backgroundMode == IMAGE. */
    val wallpaperFile: String? = null,
    /** Затемнення власних шпалер, 0..1 — щоб текст читався. */
    val wallpaperDim: Float = 0.25f,
    val fontChoice: FontChoice = FontChoice.THEME,
    /** null = як у темі; інакше "" (1433), ":" (14:33) або " " (14 33). */
    val clockSeparator: String? = ":",
    // дзеркало екрана
    /**
     * Дзеркальна розкладка: алфавіт і додатки міняються місцями.
     * Керує стороною смуги, вирівнюванням рядків, годинника, планки,
     * показника літери й зоною жесту — усім одразу.
     */
    val mirrored: Boolean = false,
    /** Клац і легка вібрація при протягуванні по алфавіту. */
    val railFeedback: Boolean = true,
    /** Щільність планки під списком, 0 = вимкнено. */
    val plateAlpha: Float = 0f,
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
        swipeUp = enumOr(KEY_SWIPE_UP, GestureAction.OPEN_DRAWER),
        swipeDown = enumOr(KEY_SWIPE_DOWN, GestureAction.EXPAND_NOTIFICATIONS),
        twoFingerSwipeDown = enumOr(KEY_TWO_FINGER, GestureAction.OPEN_SETTINGS),
        iconScale = prefs.getFloat(KEY_ICON_SCALE, 1f),
        showIconsOverride = if (prefs.contains(KEY_ICONS)) prefs.getBoolean(KEY_ICONS, true) else null,
        hiddenApps = prefs.getStringSet(KEY_HIDDEN, emptySet())?.toSet() ?: emptySet(),
        iconStyle = enumOr(KEY_ICON_STYLE, IconStyle.THEME),
        iconPackPackage = prefs.getString(KEY_ICON_PACK, null)?.ifBlank { null },
        backgroundMode = enumOr(KEY_BACKGROUND, BackgroundMode.SYSTEM),
        wallpaperFile = prefs.getString(KEY_WALLPAPER, null)?.ifBlank { null },
        wallpaperDim = prefs.getFloat(KEY_WALLPAPER_DIM, 0.25f),
        fontChoice = enumOr(KEY_FONT, FontChoice.THEME),
        clockSeparator = if (prefs.contains(KEY_CLOCK_SEP)) prefs.getString(KEY_CLOCK_SEP, ":") else ":",
        showLabels = prefs.getBoolean(KEY_SHOW_LABELS, true),
        homeOrder = enumOr(KEY_HOME_ORDER, HomeOrder.MANUAL),
        // Хто вже перемкнув стару «смугу ліворуч» — отримує повне дзеркало,
        // бо саме цього він і хотів: сама лише смуга зліва не мала сенсу.
        mirrored = prefs.getBoolean(KEY_MIRRORED, prefs.getBoolean(KEY_RAIL_LEFT, false)),
        railFeedback = prefs.getBoolean(KEY_RAIL_FEEDBACK, true),
        plateAlpha = prefs.getFloat(KEY_PLATE_ALPHA, 0f),
        weatherEnabled = prefs.getBoolean(KEY_WEATHER, true),
        useLocation = prefs.getBoolean(KEY_USE_LOCATION, true),
        manualCity = prefs.getString(KEY_CITY, "").orEmpty(),
        manualLat = if (prefs.contains(KEY_LAT)) prefs.getFloat(KEY_LAT, 0f).toDouble() else null,
        manualLon = if (prefs.contains(KEY_LON)) prefs.getFloat(KEY_LON, 0f).toDouble() else null,
    )

    private inline fun <reified T : Enum<T>> enumOr(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, default.name)!!) }.getOrDefault(default)

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
            putString(KEY_ICON_STYLE, next.iconStyle.name)
            putString(KEY_ICON_PACK, next.iconPackPackage.orEmpty())
            putString(KEY_BACKGROUND, next.backgroundMode.name)
            putString(KEY_WALLPAPER, next.wallpaperFile.orEmpty())
            putFloat(KEY_WALLPAPER_DIM, next.wallpaperDim)
            putString(KEY_FONT, next.fontChoice.name)
            if (next.clockSeparator == null) remove(KEY_CLOCK_SEP) else putString(KEY_CLOCK_SEP, next.clockSeparator)
            putBoolean(KEY_SHOW_LABELS, next.showLabels)
            putString(KEY_HOME_ORDER, next.homeOrder.name)
            putBoolean(KEY_MIRRORED, next.mirrored)
            putBoolean(KEY_RAIL_FEEDBACK, next.railFeedback)
            putFloat(KEY_PLATE_ALPHA, next.plateAlpha)
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
    /** Дзеркальне обмеження до [setShowLabels]: разом із назвами іконки не ховаються. */
    fun setIconsOverride(value: Boolean?) = mutate {
        if (value == false && !it.showLabels) it else it.copy(showIconsOverride = value)
    }
    fun toggleHidden(key: String) = mutate {
        it.copy(hiddenApps = if (key in it.hiddenApps) it.hiddenApps - key else it.hiddenApps + key)
    }

    fun setIconStyle(style: IconStyle) = mutate { it.copy(iconStyle = style) }
    fun setIconPack(pkg: String?) = mutate { it.copy(iconPackPackage = pkg?.ifBlank { null }) }
    fun setBackgroundMode(mode: BackgroundMode) = mutate { it.copy(backgroundMode = mode) }
    fun setWallpaper(file: String?) = mutate {
        it.copy(
            wallpaperFile = file,
            backgroundMode = if (file != null) BackgroundMode.IMAGE else it.backgroundMode,
        )
    }
    fun setWallpaperDim(value: Float) = mutate { it.copy(wallpaperDim = value.coerceIn(0f, 0.85f)) }
    fun setFont(font: FontChoice) = mutate { it.copy(fontChoice = font) }
    fun setClockSeparator(value: String?) = mutate { it.copy(clockSeparator = value) }
    fun setMirrored(value: Boolean) = mutate { it.copy(mirrored = value) }
    fun setRailFeedback(value: Boolean) = mutate { it.copy(railFeedback = value) }
    fun setPlateAlpha(value: Float) = mutate { it.copy(plateAlpha = value.coerceIn(0f, 0.8f)) }
    fun setHomeOrder(value: HomeOrder) = mutate { it.copy(homeOrder = value) }

    /**
     * Вимкнути можна щось одне. Якщо сховати і іконки, і назви, головний екран
     * стане порожнім, і повернути все назад буде нічим — рядків просто не видно.
     */
    fun setShowLabels(value: Boolean) = mutate {
        if (!value && it.showIconsOverride == false) it else it.copy(showLabels = value)
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
        private const val KEY_ICON_STYLE = "icon_style"
        private const val KEY_ICON_PACK = "icon_pack"
        private const val KEY_BACKGROUND = "background_mode"
        private const val KEY_WALLPAPER = "wallpaper_file"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_FONT = "font_choice"
        private const val KEY_CLOCK_SEP = "clock_separator"
        /** Застарілий ключ: лишається тільки щоб перенести старе значення в KEY_MIRRORED. */
        private const val KEY_RAIL_LEFT = "rail_on_left"
        private const val KEY_MIRRORED = "mirrored"
        private const val KEY_RAIL_FEEDBACK = "rail_feedback"
        private const val KEY_SHOW_LABELS = "show_labels"
        private const val KEY_PLATE_ALPHA = "plate_alpha"
        private const val KEY_HOME_ORDER = "home_order"
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
