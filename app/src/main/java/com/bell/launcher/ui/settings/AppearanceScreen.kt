package com.bell.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bell.launcher.data.BackgroundMode
import com.bell.launcher.data.FontChoice
import com.bell.launcher.data.IconStyle
import com.bell.launcher.data.LauncherSettings
import com.bell.launcher.data.WallpaperItem
import com.bell.launcher.data.Weather
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.theme.Appearance
import com.bell.launcher.theme.IconStyleSampler
import com.bell.launcher.theme.LauncherThemeData
import com.bell.launcher.theme.LocalClockFont
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.theme.parseColor
import com.bell.launcher.ui.components.AppIconImage
import com.bell.launcher.ui.components.AssetWallpaper
import com.bell.launcher.ui.components.ThemeWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Вкладки одного екрана «Вигляд». */
private enum class AppearanceTab(val title: String) {
    THEME("Тема"),
    BACKGROUND("Фон"),
    ICONS("Іконки"),
    TEXT("Текст"),
}

/**
 * Один екран замість п'яти окремих пунктів: тема, фон, шпалери, іконки, шрифт.
 *
 * Зверху — живий передперегляд домашнього екрана, знизу — вкладки з варіантами.
 * Кожен вибір застосовується одразу, тож зміну видно в картці над пальцем.
 */
@Composable
fun AppearanceScreen(
    settings: LauncherSettings,
    themes: List<LauncherThemeData>,
    sampleApps: List<AppInfo>,
    weather: Weather?,
    wallpapers: List<WallpaperItem>,
    installedPacks: List<Pair<String, String>>,
    loadWallpaper: (String, Int) -> ImageBitmap?,
    onTheme: (String) -> Unit,
    onImportTheme: () -> Unit,
    onDeleteTheme: (String) -> Unit,
    onBackground: (BackgroundMode) -> Unit,
    onWallpaper: (String?) -> Unit,
    onWallpaperDim: (Float) -> Unit,
    onSystemWallpaper: () -> Unit,
    onIconStyle: (IconStyle) -> Unit,
    onIconPack: (String?) -> Unit,
    onIconScale: (Float) -> Unit,
    onIcons: (Boolean?) -> Unit,
    onFindIconPacks: () -> Unit,
    onFont: (FontChoice) -> Unit,
    onClockSeparator: (String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(AppearanceTab.THEME) }
    val theme = LocalLauncherTheme.current

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
                Column {
                    Text("Вигляд", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        theme.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }

            HomePreview(
                settings = settings,
                sampleApps = sampleApps,
                weather = weather,
                loadWallpaper = loadWallpaper,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            TabRow(current = tab, onSelect = { tab = it })

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                when (tab) {
                    AppearanceTab.THEME -> ThemeTab(
                        themes = themes,
                        currentId = theme.id,
                        onTheme = onTheme,
                        onImportTheme = onImportTheme,
                        onDeleteTheme = onDeleteTheme,
                    )

                    AppearanceTab.BACKGROUND -> BackgroundTab(
                        settings = settings,
                        wallpapers = wallpapers,
                        loadWallpaper = loadWallpaper,
                        onBackground = onBackground,
                        onWallpaper = onWallpaper,
                        onWallpaperDim = onWallpaperDim,
                        onSystemWallpaper = onSystemWallpaper,
                    )

                    AppearanceTab.ICONS -> IconsTab(
                        settings = settings,
                        sampleApps = sampleApps,
                        installedPacks = installedPacks,
                        onIconStyle = onIconStyle,
                        onIconPack = onIconPack,
                        onIconScale = onIconScale,
                        onIcons = onIcons,
                        onFindIconPacks = onFindIconPacks,
                    )

                    AppearanceTab.TEXT -> TextTab(
                        settings = settings,
                        onFont = onFont,
                        onClockSeparator = onClockSeparator,
                    )
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

// ------------------------------------------------------------- передперегляд

/** Зменшена копія домашнього екрана: фон, годинник, рядок дати й три додатки. */
@Composable
private fun HomePreview(
    settings: LauncherSettings,
    sampleApps: List<AppInfo>,
    weather: Weather?,
    loadWallpaper: (String, Int) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val layout = theme.manifest.layout
    val header = theme.manifest.header
    val clockFont = LocalClockFont.current
    val separator = settings.clockSeparator ?: header.clockSeparator

    val labelColor = parseColor(theme.manifest.colors.homeLabel, Color.White)
    val clockColor = parseColor(header.clockColor, labelColor)

    val now = remember { LocalDateTime.now() }
    val timeText = remember(separator) {
        val pattern = if (header.hour24) "HH" else "hh"
        now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())) +
            separator +
            now.format(DateTimeFormatter.ofPattern("mm", Locale.getDefault()))
    }
    val dateText = remember {
        runCatching { now.format(DateTimeFormatter.ofPattern(header.dateFormat, Locale.getDefault())) }
            .getOrDefault(now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())))
    }
    val subLine = listOfNotNull(dateText, weather?.shortText)
        .joinToString(header.subSeparator.ifBlank { " · " })

    Box(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)),
    ) {
        PreviewBackground(settings, loadWallpaper)

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                timeText,
                color = clockColor,
                fontSize = 44.sp,
                fontWeight = FontWeight(header.clockWeight.coerceIn(100, 900)),
                fontFamily = clockFont,
                letterSpacing = header.clockLetterSpacingEm.em,
                maxLines = 1,
            )
            Text(
                subLine,
                color = clockColor.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

            sampleApps.take(3).forEach { app ->
                Row(
                    Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (settings.showIconsOverride ?: layout.showIcons) {
                        AppIconImage(
                            packageName = app.packageName,
                            activityName = app.activityName,
                            size = (layout.iconSizeDp * settings.iconScale * 0.72f).dp,
                        )
                        Spacer(Modifier.width((layout.iconGapDp * 0.7f).dp))
                    }
                    Text(
                        app.label,
                        color = labelColor,
                        fontSize = (layout.labelSizeSp * 0.8f).sp,
                        fontWeight = FontWeight(layout.labelWeight.coerceIn(100, 900)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (sampleApps.isEmpty()) {
                Text(
                    "Список додатків ще завантажується",
                    color = labelColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PreviewBackground(
    settings: LauncherSettings,
    loadWallpaper: (String, Int) -> ImageBitmap?,
) {
    val theme = LocalLauncherTheme.current
    when (settings.backgroundMode) {
        BackgroundMode.THEME -> ThemeWallpaper(theme = theme, scrollFraction = 0f)

        BackgroundMode.IMAGE -> {
            val file = settings.wallpaperFile
            // Декодування виносимо з головного потоку, інакше велике фото
            // підвішує кадр саме тоді, коли користувач гортає варіанти.
            val bitmap by produceState<ImageBitmap?>(null, file) {
                value = file?.let { name -> withContext(Dispatchers.IO) { loadWallpaper(name, 720) } }
            }
            if (bitmap == null) {
                EmptyBackgroundHint("Зображення не обрано")
            } else {
                AssetWallpaper(bitmap = bitmap, dim = settings.wallpaperDim)
            }
        }

        // Системні шпалери намалювати не можемо (Android не дає їх читати без
        // окремого дозволу), тож показуємо чесну заглушку.
        BackgroundMode.SYSTEM -> Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                ),
        ) {
            Text(
                "Шпалери телефона",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            )
        }
    }
}

@Composable
private fun EmptyBackgroundHint(text: String) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(10.dp),
        )
    }
}

// ------------------------------------------------------------------ вкладки

@Composable
private fun TabRow(current: AppearanceTab, onSelect: (AppearanceTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppearanceTab.entries.forEach { entry ->
            val selected = entry == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(entry) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ThemeTab(
    themes: List<LauncherThemeData>,
    currentId: String,
    onTheme: (String) -> Unit,
    onImportTheme: () -> Unit,
    onDeleteTheme: (String) -> Unit,
) {
    SectionLabel("Готові теми")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(themes, key = { it.id }) { item ->
            ThemeCard(
                theme = item,
                selected = item.id == currentId,
                onClick = { onTheme(item.id) },
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    ActionRow(
        title = "Імпортувати тему",
        subtitle = "Файл .ltheme або .zip з manifest.json",
        onClick = onImportTheme,
    )

    val removable = themes.filter { !it.id.startsWith("builtin.") }
    if (removable.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionLabel("Встановлені теми")
        removable.forEach { item ->
            ActionRow(
                title = item.name,
                subtitle = "Натисніть, щоб видалити",
                onClick = { onDeleteTheme(item.id) },
            )
        }
    }
}

@Composable
private fun ThemeCard(theme: LauncherThemeData, selected: Boolean, onClick: () -> Unit) {
    val colors = theme.manifest.colors
    val background = parseColor(colors.background, Color.Black)
    val label = parseColor(colors.homeLabel, Color.White)
    val primary = parseColor(colors.primary, Color.White)

    Column(
        Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(10.dp),
        ) {
            Column {
                Text("14:33", color = label, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                repeat(3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(primary.copy(alpha = 0.85f))
                        )
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier
                                .height(5.dp)
                                .width(38.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(label.copy(alpha = 0.55f))
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BackgroundTab(
    settings: LauncherSettings,
    wallpapers: List<WallpaperItem>,
    loadWallpaper: (String, Int) -> ImageBitmap?,
    onBackground: (BackgroundMode) -> Unit,
    onWallpaper: (String?) -> Unit,
    onWallpaperDim: (Float) -> Unit,
    onSystemWallpaper: () -> Unit,
) {
    SectionLabel("Що показувати позаду списку")
    ChipRow(
        options = BackgroundMode.entries.map { it.title to it },
        isSelected = { it == settings.backgroundMode },
        onSelect = onBackground,
    )

    if (settings.backgroundMode == BackgroundMode.IMAGE) {
        Spacer(Modifier.height(12.dp))
        SectionLabel("Шпалери лаунчера")
        if (wallpapers.isEmpty()) {
            Text(
                "Покладіть зображення у папку проєкту app/src/main/assets/wallpapers " +
                    "і зберіть APK — вони з'являться тут.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(wallpapers, key = { it.fileName }) { item ->
                    WallpaperThumb(
                        item = item,
                        selected = item.fileName == settings.wallpaperFile,
                        loadWallpaper = loadWallpaper,
                        onClick = { onWallpaper(item.fileName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SliderRow(
                title = "Затемнення",
                value = settings.wallpaperDim,
                range = 0f..0.85f,
                onChange = onWallpaperDim,
            )
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    ActionRow(
        title = "Змінити шпалери системи",
        subtitle = "Відкриє галерею Android — видно в режимі «Системні шпалери»",
        onClick = onSystemWallpaper,
    )
}

@Composable
private fun WallpaperThumb(
    item: WallpaperItem,
    selected: Boolean,
    loadWallpaper: (String, Int) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, item.fileName) {
        value = withContext(Dispatchers.IO) { loadWallpaper(item.fileName, 320) }
    }
    Box(
        Modifier
            .width(84.dp)
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun IconsTab(
    settings: LauncherSettings,
    sampleApps: List<AppInfo>,
    installedPacks: List<Pair<String, String>>,
    onIconStyle: (IconStyle) -> Unit,
    onIconPack: (String?) -> Unit,
    onIconScale: (Float) -> Unit,
    onIcons: (Boolean?) -> Unit,
    onFindIconPacks: () -> Unit,
) {
    SectionLabel("Стиль іконок")
    val sample = sampleApps.firstOrNull()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(IconStyle.entries.toList(), key = { it.name }) { style ->
            IconStyleCard(
                style = style,
                settings = settings,
                sample = sample,
                selected = style == settings.iconStyle,
                onClick = { onIconStyle(style) },
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionLabel("Папка іконок")
    ChipRow(
        options = buildList<Pair<String, String?>> {
            add("З теми" to null)
            installedPacks.forEach { (pkg, label) -> add(label to pkg) }
        },
        isSelected = { it == settings.iconPackPackage },
        onSelect = onIconPack,
    )
    Text(
        if (installedPacks.isEmpty()) {
            "«З теми» бере картинки з папки icons усередині теми, а якщо їх там немає — " +
                "малює системну іконку в обраному стилі. Встановлені з Play Market паки " +
                "з'являться в цьому ж рядку."
        } else {
            "Знайдено паків на пристрої: ${installedPacks.size}. " +
                "«З теми» — вшиті іконки теми плюс обраний стиль."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 6.dp),
    )
    ActionRow(
        title = "Знайти паки іконок",
        subtitle = "Відкриє пошук «icon pack» у Play Market",
        onClick = onFindIconPacks,
    )

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SliderRow(
        title = "Розмір іконок",
        value = settings.iconScale,
        range = 0.7f..1.5f,
        onChange = onIconScale,
    )
    SwitchRow(
        title = "Показувати іконки",
        checked = settings.showIconsOverride ?: true,
        onChange = { onIcons(it) },
    )
}

@Composable
private fun IconStyleCard(
    style: IconStyle,
    settings: LauncherSettings,
    sample: AppInfo?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val sizePx = with(LocalDensity.current) { 44.dp.roundToPx() }

    var image by remember(style, theme.id, sample?.key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(style, theme.id, sample?.key) {
        val app = sample ?: return@LaunchedEffect
        image = IconStyleSampler.render(
            context = context,
            theme = theme,
            settings = settings,
            style = style,
            packageName = app.packageName,
            activityName = app.activityName,
            sizePx = sizePx,
        )
    }

    Column(
        Modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            image?.let { Image(bitmap = it, contentDescription = style.title, Modifier.size(44.dp)) }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            style.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TextTab(
    settings: LauncherSettings,
    onFont: (FontChoice) -> Unit,
    onClockSeparator: (String) -> Unit,
) {
    SectionLabel("Шрифт")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(FontChoice.entries.toList(), key = { it.name }) { choice ->
            val selected = choice == settings.fontChoice
            val family = remember(choice) { Appearance.fontFamily(choice) }
            Column(
                Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onFont(choice) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "14:33",
                    fontFamily = family,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    choice.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SectionLabel("Формат часу")
    ChipRow(
        options = listOf("14:33" to ":", "1433" to "", "14 33" to " "),
        isSelected = { it == (settings.clockSeparator ?: ":") },
        onSelect = onClockSeparator,
    )
}

// ------------------------------------------------------------ дрібні деталі

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(options.size) { index ->
            val (label, value) = options[index]
            val selected = isSelected(value)
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${Math.round(value * 100)} %", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
