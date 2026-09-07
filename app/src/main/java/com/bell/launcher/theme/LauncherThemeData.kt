package com.bell.launcher.theme

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bell.launcher.theme.model.ThemeManifest

/** Тема, готова до використання: маніфест + джерело файлів. */
@Immutable
data class LauncherThemeData(
    val manifest: ThemeManifest,
    val source: ThemeSource,
    val removable: Boolean = false,
) {
    val id: String get() = manifest.id
    val name: String get() = manifest.name

    fun previewBitmap(): ImageBitmap? {
        val path = manifest.preview ?: manifest.wallpaper.image ?: return null
        return source.open(path)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }
}

/** Парсинг hex-кольору "#RRGGBB" або "#AARRGGBB". Повертає [fallback] при помилці. */
fun parseColor(value: String?, fallback: Color = Color.Transparent): Color {
    if (value.isNullOrBlank()) return fallback
    return runCatching {
        val hex = value.trim().removePrefix("#")
        val long = hex.toLong(16)
        when (hex.length) {
            6 -> Color(0xFF000000L or long)
            8 -> Color(long)
            3 -> {
                val r = hex[0].digitToInt(16) * 17
                val g = hex[1].digitToInt(16) * 17
                val b = hex[2].digitToInt(16) * 17
                Color(r, g, b)
            }
            else -> fallback
        }
    }.getOrDefault(fallback)
}

fun parseColorInt(value: String?, fallback: Int = 0): Int {
    if (value.isNullOrBlank()) return fallback
    return runCatching { android.graphics.Color.parseColor(value.trim()) }.getOrDefault(fallback)
}
