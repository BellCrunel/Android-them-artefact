package com.bell.launcher.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Одне зображення з папки assets/wallpapers. */
data class WallpaperItem(
    val fileName: String,
    val title: String,
)

/**
 * Читає шпалери, які користувач поклав у `app/src/main/assets/wallpapers/`.
 * Нічого налаштовувати не треба — просто кинути файл у папку і перезібрати.
 */
class WallpaperRepository(private val context: Context) {

    private val extensions = setOf("jpg", "jpeg", "png", "webp")

    fun list(): List<WallpaperItem> = runCatching {
        context.assets.list(FOLDER).orEmpty()
            .filter { it.substringAfterLast('.', "").lowercase() in extensions }
            .sorted()
            .map { file ->
                WallpaperItem(
                    fileName = file,
                    title = file.substringBeforeLast('.').replace('_', ' ').replace('-', ' '),
                )
            }
    }.getOrDefault(emptyList())

    fun load(fileName: String, maxWidth: Int = 1440): ImageBitmap? = runCatching {
        // Спершу читаємо тільки розміри, щоб не тягнути в пам'ять велике фото цілком
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("$FOLDER/$fileName").use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (bounds.outWidth / sample > maxWidth) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.assets.open("$FOLDER/$fileName").use {
            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
        }
    }.getOrNull()

    companion object {
        const val FOLDER = "wallpapers"
    }
}
