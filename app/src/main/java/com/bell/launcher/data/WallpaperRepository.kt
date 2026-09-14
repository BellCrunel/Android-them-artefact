package com.bell.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Одне зображення для фону лаунчера.
 *
 * [removable] = false для вбудованих в APK: їх фізично немає на диску,
 * видалити їх можна тільки перезбіркою.
 */
data class WallpaperItem(
    val fileName: String,
    val title: String,
    val removable: Boolean = false,
)

/**
 * Два джерела шпалер:
 *  - вбудовані в `assets/wallpapers/` — постачаються з APK, лише для читання;
 *  - додані користувачем — файли в `filesDir/wallpapers/`, їх можна додавати
 *    й видаляти прямо з налаштувань.
 *
 * Картинку з галереї саме **копіюємо**, а не запам'ятовуємо `Uri`: дозвіл на
 * чужий `Uri` живе до перезавантаження телефона, після чого шпалери зникли б.
 */
class WallpaperRepository(private val context: Context) {

    private val extensions = setOf("jpg", "jpeg", "png", "webp")

    private val userDir: File
        get() = File(context.filesDir, FOLDER).apply { if (!exists()) mkdirs() }

    fun list(): List<WallpaperItem> = bundled() + user()

    private fun bundled(): List<WallpaperItem> = runCatching {
        context.assets.list(FOLDER).orEmpty()
            .filter { it.extension() in extensions }
            .sorted()
            .map { WallpaperItem(fileName = it, title = it.prettyTitle(), removable = false) }
    }.getOrDefault(emptyList())

    private fun user(): List<WallpaperItem> = runCatching {
        userDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.extension() in extensions }
            .sortedByDescending { it.lastModified() }
            .map {
                WallpaperItem(
                    fileName = USER_PREFIX + it.name,
                    title = it.name.prettyTitle(),
                    removable = true,
                )
            }
    }.getOrDefault(emptyList())

    /**
     * Копіює зображення з галереї до себе, одразу зменшивши під розмір екрана.
     *
     * Без зменшення фото з сучасної камери важить 5–10 МБ: `filesDir` розпухне,
     * а декодування при кожному показі почне гальмувати.
     */
    suspend fun import(uri: Uri, maxWidth: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = open(uri)?.use { decodeScaled(it, { open(uri) }, maxWidth) }
                ?: return@runCatching null
            val file = File(userDir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            USER_PREFIX + file.name
        }.getOrNull()
    }

    /** true, якщо файл справді видалено. Вбудовані шпалери не видаляються. */
    fun delete(fileName: String): Boolean {
        if (!fileName.startsWith(USER_PREFIX)) return false
        val file = File(userDir, fileName.removePrefix(USER_PREFIX))
        return runCatching { file.delete() }.getOrDefault(false)
    }

    fun load(fileName: String, maxWidth: Int = 1440): ImageBitmap? = runCatching {
        decodeScaled(openSource(fileName) ?: return@runCatching null, { openSource(fileName) }, maxWidth)
            ?.asImageBitmap()
    }.getOrNull()

    private fun openSource(fileName: String): InputStream? = runCatching {
        if (fileName.startsWith(USER_PREFIX)) {
            File(userDir, fileName.removePrefix(USER_PREFIX)).inputStream()
        } else {
            context.assets.open("$FOLDER/$fileName")
        }
    }.getOrNull()

    private fun open(uri: Uri): InputStream? =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    /**
     * Декодує зменшену копію. Потік доводиться відкривати двічі — спершу заради
     * розмірів, потім заради самих пікселів: `InputStream` із ContentResolver
     * не перемотується назад.
     */
    private fun decodeScaled(first: InputStream, reopen: () -> InputStream?, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        first.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxWidth) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return reopen()?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun String.extension(): String = substringAfterLast('.', "").lowercase()

    private fun String.prettyTitle(): String =
        substringBeforeLast('.').replace('_', ' ').replace('-', ' ')

    companion object {
        const val FOLDER = "wallpapers"
        /** Позначка «це файл користувача, а не asset». */
        const val USER_PREFIX = "user:"
    }
}
