package com.bell.launcher.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * Дістає шрифт із теми. Для тем із assets файл копіюється в cacheDir,
 * бо Compose вміє завантажувати шрифт лише з реального файла.
 */
object FontResolver {

    fun family(context: Context, theme: LauncherThemeData, path: String?): FontFamily? {
        val file = file(context, theme, path) ?: return null
        return runCatching { FontFamily(Font(file)) }.getOrNull()
    }

    private fun file(context: Context, theme: LauncherThemeData, path: String?): File? {
        if (path.isNullOrBlank()) return null

        (theme.source as? ThemeSource.Folder)?.fileOf(path)?.let { return it }

        val cacheDir = File(context.cacheDir, "theme-fonts/${sanitize(theme.id)}").apply { mkdirs() }
        val target = File(cacheDir, sanitize(path))
        if (target.isFile && target.length() > 0) return target

        val stream = theme.source.open(path) ?: return null
        return runCatching {
            stream.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.takeIf { it.length() > 0 }
        }.getOrNull()
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
