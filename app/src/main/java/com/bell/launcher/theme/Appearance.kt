package com.bell.launcher.theme

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bell.launcher.data.FontChoice
import com.bell.launcher.data.IconStyle
import com.bell.launcher.data.LauncherSettings
import com.bell.launcher.theme.model.IconShape
import com.bell.launcher.theme.model.IconSpec

/**
 * Зводить разом те, що задала тема, і те, що користувач перевизначив у налаштуваннях.
 * Налаштування завжди мають пріоритет; значення «Як у темі» лишає все як є.
 */
object Appearance {

    fun iconSpec(theme: LauncherThemeData?, settings: LauncherSettings): IconSpec {
        val base = theme?.manifest?.icons ?: IconSpec()
        val labelColor = theme?.manifest?.colors?.homeLabel ?: "#FFFFFF"

        val styled = when (settings.iconStyle) {
            IconStyle.THEME -> base
            IconStyle.CIRCLE -> base.copy(shape = IconShape.CIRCLE, scale = 0.94f, tint = null)
            IconStyle.SQUIRCLE -> base.copy(shape = IconShape.SQUIRCLE, scale = 0.94f, tint = null)
            IconStyle.TILE -> base.copy(
                shape = IconShape.ROUNDED,
                cornerRadiusPercent = 28,
                scale = 0.60f,
                background = base.background ?: "#26FFFFFF",
                tint = null,
            )
            IconStyle.PEBBLE -> base.copy(
                shape = IconShape.ROUNDED,
                cornerRadiusPercent = 46,
                scale = 0.58f,
                background = "#1FFFFFFF",
                tint = null,
            )
            IconStyle.GLASS -> base.copy(
                shape = IconShape.CIRCLE,
                scale = 0.56f,
                background = "#2BFFFFFF",
                tint = null,
            )
            IconStyle.MONO -> base.copy(
                shape = IconShape.ORIGINAL,
                scale = 0.92f,
                background = null,
                tint = labelColor,
            )
            IconStyle.ORIGINAL -> base.copy(
                shape = IconShape.ORIGINAL,
                scale = 1f,
                background = null,
                tint = null,
            )
        }

        // Пак із Play Market, обраний у налаштуваннях, перекриває той, що вказала тема.
        return styled.copy(
            iconPackPackage = settings.iconPackPackage ?: base.iconPackPackage,
        )
    }

    fun fontFamily(choice: FontChoice): FontFamily? = when (choice) {
        FontChoice.THEME -> null
        FontChoice.SANS -> FontFamily.SansSerif
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONOSPACE -> FontFamily.Monospace
        FontChoice.CURSIVE -> FontFamily.Cursive
        else -> choice.deviceName?.let { name ->
            runCatching { FontFamily(Font(DeviceFontFamilyName(name))) }.getOrNull()
        }
    }

    /** Роздільник годин і хвилин: налаштування > тема. */
    fun clockSeparator(theme: LauncherThemeData?, settings: LauncherSettings): String =
        settings.clockSeparator ?: theme?.manifest?.header?.clockSeparator ?: ":"
}
