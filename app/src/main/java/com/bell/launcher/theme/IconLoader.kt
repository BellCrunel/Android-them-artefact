package com.bell.launcher.theme

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Єдина точка отримання іконки додатка з урахуванням активної теми.
 *
 * Порядок пошуку:
 * 1. `icons.map` з manifest.json
 * 2. файл `<folder>/<package>.png|webp|jpg` усередині теми
 * 3. встановлений icon pack, якщо тема вказала `icons.iconPackPackage`
 * 4. системна іконка, приведена до форми теми
 */
class IconLoader(private val context: Context) {

    private val pm = context.packageManager
    private var theme: LauncherThemeData? = null
    private var iconPack: IconPackLoader? = null
    private val cache = LruCache<String, ImageBitmap>(400)

    @Synchronized
    fun setTheme(newTheme: LauncherThemeData?) {
        if (newTheme?.source?.key == theme?.source?.key && newTheme?.id == theme?.id) return
        theme = newTheme
        cache.evictAll()
        val packName = newTheme?.manifest?.icons?.iconPackPackage
        iconPack = packName?.takeIf { it.isNotBlank() }?.let { IconPackLoader(context, it) }
    }

    suspend fun load(packageName: String, activityName: String, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = "${theme?.id}|$packageName/$activityName|$sizePx"
            cache.get(key)?.let { return@withContext it }
            val bitmap = renderIcon(packageName, activityName, sizePx) ?: return@withContext null
            val image = bitmap.asImageBitmap()
            cache.put(key, image)
            image
        }

    private fun renderIcon(packageName: String, activityName: String, sizePx: Int): Bitmap? {
        val current = theme
        val spec = current?.manifest?.icons

        // 1 + 2: іконка, що лежить усередині теми
        if (current != null && spec != null) {
            themedBitmap(current, packageName, sizePx)?.let { return it }
        }

        // 3: встановлений icon pack
        iconPack?.getIcon(packageName, activityName)?.let { drawable ->
            return spec?.let { IconRenderer.render(drawable, it.copy(shape = com.bell.launcher.theme.model.IconShape.ORIGINAL), sizePx) }
                ?: drawable.toBitmap(sizePx)
        }

        // 4: системна іконка
        val system = systemIcon(packageName, activityName) ?: return null
        return if (spec != null) IconRenderer.render(system, spec, sizePx) else system.toBitmap(sizePx)
    }

    private fun themedBitmap(themeData: LauncherThemeData, packageName: String, sizePx: Int): Bitmap? {
        val spec = themeData.manifest.icons
        val candidates = buildList {
            spec.map[packageName]?.let { add(it) }
            val folder = spec.folder.trim('/')
            listOf("png", "webp", "jpg").forEach { ext ->
                add(if (folder.isEmpty()) "$packageName.$ext" else "$folder/$packageName.$ext")
            }
        }
        for (path in candidates) {
            val stream = themeData.source.open(path) ?: continue
            val raw = stream.use { BitmapFactory.decodeStream(it) } ?: continue
            val drawable = BitmapDrawable(context.resources, raw)
            val normalized = spec.copy(shape = com.bell.launcher.theme.model.IconShape.ORIGINAL, background = null)
            return IconRenderer.render(drawable, normalized, sizePx)
        }
        return null
    }

    private fun systemIcon(packageName: String, activityName: String): Drawable? = runCatching {
        if (activityName.isNotBlank()) {
            pm.getActivityIcon(ComponentName(packageName, activityName))
        } else {
            pm.getApplicationIcon(packageName)
        }
    }.recoverCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    private fun Drawable.toBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bmp
    }
}
