package com.bell.launcher.theme

import android.content.ComponentName
import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bell.launcher.data.IconStyle
import com.bell.launcher.data.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Малює зразок іконки для екрана «Вигляд».
 *
 * Свідомо оминає [IconLoader]: той налаштований на один поточний стиль, а тут
 * треба показати всі стилі одночасно, щоб було з чого вибирати.
 */
object IconStyleSampler {

    private val cache = LruCache<String, ImageBitmap>(96)

    suspend fun render(
        context: Context,
        theme: LauncherThemeData?,
        settings: LauncherSettings,
        style: IconStyle,
        packageName: String,
        activityName: String,
        sizePx: Int,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "${theme?.id}|${style.name}|$packageName|$sizePx"
        cache.get(key)?.let { return@withContext it }

        val pm = context.packageManager
        val drawable = runCatching {
            if (activityName.isNotBlank()) {
                pm.getActivityIcon(ComponentName(packageName, activityName))
            } else {
                pm.getApplicationIcon(packageName)
            }
        }.recoverCatching { pm.getApplicationIcon(packageName) }.getOrNull()
            ?: return@withContext null

        // Пак іконок у зразку не застосовуємо: тут показуємо саме форму стилю.
        val spec = Appearance.iconSpec(theme, settings.copy(iconStyle = style))
            .copy(iconPackPackage = null)
        val bitmap = runCatching { IconRenderer.render(drawable, spec, sizePx) }.getOrNull()
            ?: return@withContext null

        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        image
    }
}
