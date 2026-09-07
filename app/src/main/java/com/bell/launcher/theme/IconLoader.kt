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
import com.bell.launcher.theme.model.IconShape
import com.bell.launcher.theme.model.IconSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Єдина точка отримання іконки додатка.
 *
 * Порядок пошуку:
 * 1. `icons.map` з manifest.json теми
 * 2. файл `<folder>/<package>.png|webp|jpg` усередині теми
 * 3. встановлений icon pack (тема або налаштування)
 * 4. системна іконка, приведена до форми/підкладки/тінту
 */
class IconLoader(private val context: Context) {

    private val pm = context.packageManager
    private var theme: LauncherThemeData? = null
    private var spec: IconSpec = IconSpec()
    private var iconPack: IconPackLoader? = null
    private var configKey: String = ""
    private val cache = LruCache<String, ImageBitmap>(400)

    /** Тема + фактичні параметри іконок (тема, перекрита налаштуваннями). */
    @Synchronized
    fun configure(newTheme: LauncherThemeData?, newSpec: IconSpec) {
        val key = "${newTheme?.id}|${newTheme?.source?.key}|${newSpec.hashCode()}"
        if (key == configKey) return
        configKey = key
        theme = newTheme
        spec = newSpec
        cache.evictAll()
        val packName = newSpec.iconPackPackage?.takeIf { it.isNotBlank() }
        iconPack = packName?.let { IconPackLoader(context, it) }
    }

    suspend fun load(packageName: String, activityName: String, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = "$configKey|$packageName/$activityName|$sizePx"
            cache.get(key)?.let { return@withContext it }
            // Помилка рендеру однієї іконки не повинна ронити весь лаунчер.
            val bitmap = runCatching { renderIcon(packageName, activityName, sizePx) }
                .getOrNull() ?: return@withContext null
            val image = bitmap.asImageBitmap()
            cache.put(key, image)
            image
        }

    private fun renderIcon(packageName: String, activityName: String, sizePx: Int): Bitmap? {
        // 1 + 2: іконка, що лежить усередині теми
        theme?.let { themedBitmap(it, packageName, sizePx)?.let { bmp -> return bmp } }

        // 3: встановлений icon pack
        iconPack?.getIcon(packageName, activityName)?.let { drawable ->
            return IconRenderer.render(drawable, spec.copy(shape = IconShape.ORIGINAL), sizePx)
        }

        // 4: системна іконка
        val system = systemIcon(packageName, activityName) ?: return null
        return IconRenderer.render(system, spec, sizePx)
    }

    private fun themedBitmap(themeData: LauncherThemeData, packageName: String, sizePx: Int): Bitmap? {
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
            val normalized = spec.copy(shape = IconShape.ORIGINAL, background = null)
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
}
