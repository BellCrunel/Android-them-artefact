package com.bell.launcher.theme.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Опис теми лаунчера. Живе у файлі `manifest.json` всередині .ltheme (zip) або папки теми.
 * Усі поля, крім [id] та [name], мають значення за замовчуванням — тема може бути мінімальною.
 *
 * Формат v2: домашній екран — вертикальний список із заголовком-годинником.
 */
@Serializable
data class ThemeManifest(
    val formatVersion: Int = 2,
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val preview: String? = null,
    val wallpaper: WallpaperSpec = WallpaperSpec(),
    val colors: ColorSpec = ColorSpec(),
    val typography: TypographySpec = TypographySpec(),
    val header: HeaderSpec = HeaderSpec(),
    val icons: IconSpec = IconSpec(),
    val layout: LayoutSpec = LayoutSpec(),
    val effects: EffectSpec = EffectSpec(),
)

@Serializable
data class WallpaperSpec(
    /** Відносний шлях до зображення всередині теми, напр. "wallpaper.jpg". */
    val image: String? = null,
    /** Градієнт-фолбек, якщо зображення немає: список hex-кольорів згори вниз. */
    val gradient: List<String> = emptyList(),
    val dim: Float = 0f,
    /** Легкий паралакс при скролі списку. */
    val parallaxOnScroll: Boolean = true,
)

@Serializable
data class ColorSpec(
    val dark: Boolean = true,
    val primary: String = "#7C4DFF",
    val onPrimary: String = "#FFFFFF",
    val secondary: String = "#00E5FF",
    val background: String = "#0B0B12",
    val onBackground: String = "#EDEDF5",
    val surface: String = "#15151F",
    val onSurface: String = "#EDEDF5",
    val outline: String = "#33FFFFFF",
    /** Колір назв додатків на домашньому екрані. */
    val homeLabel: String = "#FFFFFF",
    /** Колір назв додатків у шухляді. */
    val drawerLabel: String = "#EDEDF5",
    /** Тінь під назвами (прозорий = без тіні). */
    val labelShadow: String = "#B3000000",
    /** Колір літер алфавітного скролу. */
    val scrubber: String = "#FFFFFF",
)

@Serializable
data class TypographySpec(
    /** Основний шрифт теми: відносний шлях до .ttf/.otf усередині теми. */
    val fontFamily: String? = null,
    val scale: Float = 1.0f,
    val allCaps: Boolean = false,
)

@Serializable
enum class HeaderStyle {
    /** Великі цифри часу, під ними рядок «дата · погода». */
    @SerialName("bigDigits") BIG_DIGITS,

    /** Тільки рядок «час · дата · погода», без великих цифр. */
    @SerialName("compact") COMPACT,

    /** Без заголовка взагалі. */
    @SerialName("none") NONE,
}

@Serializable
enum class AlignMode {
    @SerialName("start") START,
    @SerialName("center") CENTER,
    @SerialName("end") END,
}

/** Заголовок домашнього екрана: годинник, дата, погода. */
@Serializable
data class HeaderSpec(
    val style: HeaderStyle = HeaderStyle.BIG_DIGITS,
    /** Окремий шрифт для цифр годинника; якщо null — береться typography.fontFamily. */
    val clockFont: String? = null,
    val clockSizeSp: Float = 76f,
    val clockColor: String? = null,
    /** Міжлітерний інтервал у em; від'ємне значення стискає цифри як на скрінах. */
    val clockLetterSpacingEm: Float = -0.04f,
    /** 100..900 */
    val clockWeight: Int = 700,
    val hour24: Boolean = true,
    /** Роздільник між годинами й хвилинами: "" → 1041, ":" → 10:41. */
    val clockSeparator: String = "",
    /** Формат дати за шаблоном java.time, напр. "EEE, d MMM" → «Tue, 30 Dec». */
    val dateFormat: String = "EEE, d MMM",
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    /** Роздільник елементів у нижньому рядку, напр. " · ". */
    val subSeparator: String = " ",
    val subColor: String? = null,
    val subSizeSp: Float = 14f,
    val align: AlignMode = AlignMode.START,
    val paddingTopDp: Int = 52,
    val paddingBottomDp: Int = 26,
)

@Serializable
enum class IconShape {
    @SerialName("original") ORIGINAL,
    @SerialName("circle") CIRCLE,
    @SerialName("squircle") SQUIRCLE,
    @SerialName("rounded") ROUNDED,
    @SerialName("square") SQUARE,
    @SerialName("teardrop") TEARDROP,
    @SerialName("hexagon") HEXAGON,
}

@Serializable
data class IconSpec(
    val shape: IconShape = IconShape.SQUIRCLE,
    /** Радіус кутів у відсотках від розміру іконки, використовується для ROUNDED. */
    val cornerRadiusPercent: Int = 28,
    /** Масштаб самої іконки всередині форми, 0.4..1.0 */
    val scale: Float = 0.94f,
    /** Колір підкладки. Порожньо = без підкладки (лише обрізання по формі). */
    val background: String? = null,
    /** Накласти форму на звичайні (не адаптивні) іконки. */
    val normalizeLegacyIcons: Boolean = true,
    /** Пакет встановленого icon pack (ADW/Nova). Якщо задано — має пріоритет над bundled. */
    val iconPackPackage: String? = null,
    /** Папка з іконками всередині теми; файли називаються як package name. */
    val folder: String = "icons",
    /** Явні відповідності package → файл, якщо назви не збігаються. */
    val map: Map<String, String> = emptyMap(),
    /** Монохромний режим: перефарбувати всі іконки в один колір. */
    val tint: String? = null,
)

/** Вертикальний список додатків. */
@Serializable
data class LayoutSpec(
    val iconSizeDp: Int = 42,
    /** Відстань між рядками. */
    val rowSpacingDp: Int = 12,
    /** Проміжок між іконкою і назвою. */
    val iconGapDp: Int = 18,
    val horizontalPaddingDp: Int = 26,
    val align: AlignMode = AlignMode.START,
    val showIcons: Boolean = true,
    val labelSizeSp: Float = 17f,
    /** 100..900 */
    val labelWeight: Int = 500,
    /** Алфавітний скрол справа в шухляді. */
    val drawerScrubber: Boolean = true,
    /** Іконки в шухляді (можна вимкнути для суто текстового списку). */
    val drawerShowIcons: Boolean = true,
    val statusBarDark: Boolean = false,
)

@Serializable
data class EffectSpec(
    /** Непрозорість фону шухляди, 0..1 */
    val drawerScrim: Float = 0.86f,
    val animationSpeed: Float = 1.0f,
    /** Сила паралаксу шпалер при скролі, 0 = вимкнено. */
    val parallax: Float = 0.25f,
)
