package com.bell.launcher.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

val LocalLauncherTheme = staticCompositionLocalOf<LauncherThemeData> {
    error("LauncherTheme не ініціалізовано")
}

/** Шрифт для великих цифр годинника (може відрізнятися від основного шрифта теми). */
val LocalClockFont = staticCompositionLocalOf<FontFamily?> { null }

/** Роздільник годин і хвилин з урахуванням налаштувань користувача. */
val LocalClockSeparator = staticCompositionLocalOf { ":" }

@Composable
fun LauncherTheme(
    themeData: LauncherThemeData,
    fontOverride: FontFamily? = null,
    clockSeparator: String = ":",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val c = themeData.manifest.colors

    val scheme = remember(themeData.id) {
        val primary = parseColor(c.primary, Color(0xFF7C4DFF))
        val onPrimary = parseColor(c.onPrimary, Color.White)
        val secondary = parseColor(c.secondary, Color(0xFF00E5FF))
        val background = parseColor(c.background, Color(0xFF0B0B12))
        val onBackground = parseColor(c.onBackground, Color(0xFFEDEDF5))
        val surface = parseColor(c.surface, Color(0xFF15151F))
        val onSurface = parseColor(c.onSurface, Color(0xFFEDEDF5))
        val outline = parseColor(c.outline, Color(0x33FFFFFF))

        if (c.dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                onSecondary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surface,
                onSurfaceVariant = onSurface.copy(alpha = 0.75f),
                outline = outline,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                onSecondary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surface,
                onSurfaceVariant = onSurface.copy(alpha = 0.75f),
                outline = outline,
            )
        }
    }

    val themeFont = remember(themeData.id) {
        FontResolver.family(context, themeData, themeData.manifest.typography.fontFamily)
    }
    val bodyFont = fontOverride ?: themeFont

    val clockFont = remember(themeData.id, fontOverride) {
        fontOverride
            ?: FontResolver.family(context, themeData, themeData.manifest.header.clockFont)
            ?: themeFont
    }

    val typography = remember(bodyFont, themeData.id) {
        val base = Typography()
        if (bodyFont == null) base else Typography(
            displayLarge = base.displayLarge.copy(fontFamily = bodyFont),
            displayMedium = base.displayMedium.copy(fontFamily = bodyFont),
            displaySmall = base.displaySmall.copy(fontFamily = bodyFont),
            headlineLarge = base.headlineLarge.copy(fontFamily = bodyFont),
            headlineMedium = base.headlineMedium.copy(fontFamily = bodyFont),
            headlineSmall = base.headlineSmall.copy(fontFamily = bodyFont),
            titleLarge = base.titleLarge.copy(fontFamily = bodyFont),
            titleMedium = base.titleMedium.copy(fontFamily = bodyFont),
            titleSmall = base.titleSmall.copy(fontFamily = bodyFont),
            bodyLarge = base.bodyLarge.copy(fontFamily = bodyFont),
            bodyMedium = base.bodyMedium.copy(fontFamily = bodyFont),
            bodySmall = base.bodySmall.copy(fontFamily = bodyFont),
            labelLarge = base.labelLarge.copy(fontFamily = bodyFont),
            labelMedium = base.labelMedium.copy(fontFamily = bodyFont),
            labelSmall = base.labelSmall.copy(fontFamily = bodyFont),
        )
    }

    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Без цього Text без явного кольору бере LocalContentColor, який за
        // замовчуванням ЧОРНИЙ — на темній темі текст просто зникає.
        CompositionLocalProvider(
            LocalLauncherTheme provides themeData,
            LocalClockFont provides clockFont,
            LocalClockSeparator provides clockSeparator,
            LocalContentColor provides scheme.onBackground,
            content = content,
        )
    }
}
