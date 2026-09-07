package com.bell.launcher.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.bell.launcher.theme.LauncherThemeData
import com.bell.launcher.theme.parseColor

/**
 * Фон із файлу, який користувач поклав у assets/wallpapers.
 */
@Composable
fun AssetWallpaper(
    bitmap: ImageBitmap?,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim.coerceIn(0f, 1f)))
            )
        }
    }
}

/**
 * Фон лаунчера: зображення з теми або градієнт, з легким паралаксом при скролі списку.
 */
@Composable
fun ThemeWallpaper(
    theme: LauncherThemeData,
    scrollFraction: Float,
    modifier: Modifier = Modifier,
) {
    val spec = theme.manifest.wallpaper
    val parallax = if (spec.parallaxOnScroll) theme.manifest.effects.parallax else 0f

    val bitmap: ImageBitmap? = remember(theme.id) {
        spec.image?.let { path ->
            theme.source.open(path)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        }
    }

    Box(modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (parallax > 0f) {
                            translationY = -scrollFraction.coerceIn(0f, 1f) * size.height * 0.08f * parallax
                            scaleX = 1.1f
                            scaleY = 1.1f
                        }
                    },
            )
        } else {
            val colors = remember(theme.id) {
                val list = spec.gradient.map { parseColor(it, Color.Black) }
                when {
                    list.size >= 2 -> list
                    list.size == 1 -> listOf(list.first(), list.first())
                    else -> listOf(
                        parseColor(theme.manifest.colors.background, Color.Black),
                        parseColor(theme.manifest.colors.surface, Color.Black),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors))
                    .graphicsLayer {
                        if (parallax > 0f) {
                            translationY = -scrollFraction.coerceIn(0f, 1f) * size.height * 0.03f * parallax
                        }
                    }
            )
        }

        if (spec.dim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = spec.dim.coerceIn(0f, 1f)))
            )
        }
    }
}
