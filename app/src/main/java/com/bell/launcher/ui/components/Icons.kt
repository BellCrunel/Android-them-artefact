package com.bell.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bell.launcher.data.model.AppRef
import com.bell.launcher.theme.IconLoader
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.theme.model.AlignMode

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("IconLoader не наданий") }

/** Користувацький множник розміру іконок поверх значення з теми. */
val LocalIconScale = staticCompositionLocalOf { 1f }

/** Користувацьке перевизначення показу іконок (null = як у темі). */
val LocalIconsOverride = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun AppIconImage(
    packageName: String,
    activityName: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val loader = LocalIconLoader.current
    val themeId = LocalLauncherTheme.current.id
    val px = with(LocalDensity.current) { size.roundToPx() }

    // Якщо іконка вже в кеші — беремо одразу, без кадру з порожнім місцем
    var image by remember(packageName, activityName, themeId, px) {
        mutableStateOf(loader.peek(packageName, activityName, px))
    }

    LaunchedEffect(packageName, activityName, themeId, px) {
        if (image == null) {
            image = loader.load(packageName, activityName, px)
        }
    }

    val bitmap = image
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(Color.White.copy(alpha = 0.06f))
        )
    }
}

/**
 * Рядок списку: іконка ліворуч, назва праворуч — як на скрінах.
 */
@Composable
fun AppRow(
    label: String,
    labelColor: Color,
    shadowColor: Color,
    iconSize: Dp,
    iconGap: Dp,
    labelSizeSp: Float,
    labelWeight: Int,
    allCaps: Boolean,
    align: AlignMode,
    showIcon: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val arrangement = when (align) {
        AlignMode.START -> Arrangement.Start
        AlignMode.CENTER -> Arrangement.Center
        AlignMode.END -> Arrangement.End
    }
    // Справжнє дзеркало ставить іконку до краю екрана, а назву від неї всередину.
    // Самого лише Arrangement.End замало: він переносить пару, не міняючи її порядок.
    val mirrored = align == AlignMode.END

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement,
    ) {
        // Вага потрібна з обох боків: без неї довга назва виштовхує іконку за екран.
        val labelModifier = if (align == AlignMode.CENTER) {
            Modifier
        } else {
            Modifier.weight(1f, fill = false)
        }
        val labelStyle = TextStyle(
            color = labelColor,
            fontSize = labelSizeSp.sp,
            fontWeight = FontWeight(labelWeight.coerceIn(100, 900)),
            shadow = Shadow(color = shadowColor, blurRadius = 6f),
        )
        val text = if (allCaps) label.uppercase() else label

        if (mirrored) {
            if (trailing != null) {
                trailing()
                Spacer(Modifier.width(8.dp))
            }
            if (showLabel) {
                Text(text, labelModifier, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showIcon) Spacer(Modifier.width(iconGap))
            }
            if (showIcon) {
                Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { icon() }
            }
        } else {
            if (showIcon) {
                Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { icon() }
                if (showLabel) Spacer(Modifier.width(iconGap))
            }
            if (showLabel) {
                Text(text, labelModifier, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** Мініатюра папки: сітка 2×2 з перших чотирьох іконок. */
@Composable
fun FolderPreview(
    apps: List<AppRef>,
    size: Dp,
    background: Color,
    modifier: Modifier = Modifier,
) {
    val inner = size / 2.6f
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.4f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(size / 24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            apps.take(4).chunked(2).forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(size / 24)) {
                    rowApps.forEach { ref ->
                        AppIconImage(ref.packageName, ref.activityName, inner)
                    }
                    if (rowApps.size == 1) Box(Modifier.size(inner))
                }
            }
        }
    }
}

@Composable
fun SimpleLabel(
    text: String,
    color: Color,
    sizeSp: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = sizeSp.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}
