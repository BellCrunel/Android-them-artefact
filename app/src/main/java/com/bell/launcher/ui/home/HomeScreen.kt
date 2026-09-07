package com.bell.launcher.ui.home

import android.widget.FrameLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bell.launcher.data.AppNotification
import com.bell.launcher.data.Weather
import com.bell.launcher.data.model.AppEntry
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.data.model.AppRef
import com.bell.launcher.data.model.FolderEntry
import com.bell.launcher.data.model.HomeEntry
import com.bell.launcher.data.model.WidgetEntry
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.theme.parseColor
import com.bell.launcher.ui.LauncherUiState
import com.bell.launcher.ui.components.AppIconImage
import com.bell.launcher.ui.components.AppRow
import com.bell.launcher.ui.components.FolderPreview
import com.bell.launcher.ui.components.LocalIconScale
import com.bell.launcher.ui.components.LocalIconsOverride
import com.bell.launcher.ui.components.NotificationDot
import com.bell.launcher.ui.components.SwipeableRow
import com.bell.launcher.ui.widget.LocalWidgetController
import com.bell.launcher.util.IndexLetters

@Composable
fun HomeScreen(
    state: LauncherUiState,
    weather: Weather?,
    listState: LazyListState,
    notifications: Map<String, AppNotification>,
    onLaunch: (AppRef) -> Unit,
    onAction: (HomeEntry, HomeRowAction) -> Unit,
    onFolderAppRemove: (String, AppRef) -> Unit,
    onLongPressEmpty: () -> Unit,
    onOpenNotification: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val labelColor = parseColor(theme.manifest.colors.homeLabel, Color.White)
    val padH = spec.horizontalPaddingDp.dp

    // Групування всіх додатків за літерою покажчика (кирилиця → латиниця).
    val grouped = remember(state.apps, state.settings.hiddenApps) {
        state.apps
            .filter { it.key !in state.settings.hiddenApps }
            .groupBy { IndexLetters.of(it.label) }
            .mapValues { (_, list) -> list.sortedBy { it.label.lowercase() } }
    }
    val letters = remember(grouped) {
        IndexLetters.sortLetters(listOf(IndexLetters.FAVORITES) + grouped.keys)
    }

    var activeLetter by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {

        Crossfade(
            targetState = activeLetter,
            animationSpec = tween(180),
            label = "homeMode",
            modifier = Modifier.fillMaxSize(),
        ) { letter ->
            if (letter == null || letter == IndexLetters.FAVORITES) {
                FavoritesList(
                    state = state,
                    weather = weather,
                    listState = listState,
                    notifications = notifications,
                    padH = padH,
                    onLaunch = onLaunch,
                    onAction = onAction,
                    onFolderAppRemove = onFolderAppRemove,
                    onLongPressEmpty = onLongPressEmpty,
                    onOpenNotification = onOpenNotification,
                    onDismissNotification = onDismissNotification,
                )
            } else {
                LetterList(
                    letter = letter,
                    apps = grouped[letter].orEmpty(),
                    notifications = notifications,
                    padH = padH,
                    labelColor = labelColor,
                    onLaunch = { app -> onLaunch(app.toRef()) },
                    onOpenNotification = onOpenNotification,
                    onDismissNotification = onDismissNotification,
                    onDismissLetter = { activeLetter = null },
                )
            }
        }

        LetterRail(
            letters = letters,
            active = activeLetter,
            color = parseColor(theme.manifest.colors.scrubber, Color.White),
            bubbleColor = parseColor(theme.manifest.colors.surface, Color.DarkGray)
                .copy(alpha = 0.92f),
            bubbleTextColor = parseColor(theme.manifest.colors.onSurface, Color.White),
            onActiveChange = { activeLetter = it },
            onRelease = { /* лишаємо літеру, щоб можна було натиснути додаток */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(end = 2.dp, bottom = 64.dp),
        )
    }
}

// ------------------------------------------------------------ обрані додатки

@Composable
private fun FavoritesList(
    state: LauncherUiState,
    weather: Weather?,
    listState: LazyListState,
    notifications: Map<String, AppNotification>,
    padH: androidx.compose.ui.unit.Dp,
    onLaunch: (AppRef) -> Unit,
    onAction: (HomeEntry, HomeRowAction) -> Unit,
    onFolderAppRemove: (String, AppRef) -> Unit,
    onLongPressEmpty: () -> Unit,
    onOpenNotification: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val entries = state.layout.sorted
    val expanded = rememberExpandedFolders()

    val emptyAreaModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { onLongPressEmpty() })
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(end = 44.dp),
        // Головний екран не скролиться: інакше свайп угору (перед відкриттям пошуку)
        // піднімав би годинник у самий верх. Довгі списки — це вже алфавіт.
        userScrollEnabled = false,
    ) {
        item(key = "__header__") {
            Box(emptyAreaModifier.fillMaxWidth().padding(horizontal = padH)) {
                ClockHeader(weather = weather)
            }
        }

        if (entries.isEmpty()) {
            item(key = "__empty__") {
                Column(emptyAreaModifier.fillMaxWidth().padding(horizontal = padH)) {
                    Text(
                        text = "Порожньо. Проведіть пальцем по алфавіту справа,\n" +
                            "або довгий тап → Налаштування → Обрані додатки.",
                        color = parseColor(theme.manifest.colors.homeLabel, Color.White)
                            .copy(alpha = 0.7f),
                    )
                }
            }
        }

        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            Column(Modifier.fillMaxWidth().padding(horizontal = padH)) {
                HomeRow(
                    entry = entry,
                    index = index,
                    total = entries.size,
                    isExpanded = entry.id in expanded,
                    notification = (entry as? AppEntry)?.let { notifications[it.app.packageName] },
                    onLaunch = onLaunch,
                    onToggleFolder = {
                        if (entry.id in expanded) expanded.remove(entry.id) else expanded.add(entry.id)
                    },
                    onAction = onAction,
                    onOpenNotification = onOpenNotification,
                    onDismissNotification = onDismissNotification,
                )

                if (entry is FolderEntry && entry.id in expanded) {
                    FolderChildren(
                        folder = entry,
                        onLaunch = onLaunch,
                        onRemove = { ref -> onFolderAppRemove(entry.id, ref) },
                    )
                }

                Spacer(Modifier.height(spec.rowSpacingDp.dp))
            }
        }

        item(key = "__tail__") {
            Box(emptyAreaModifier.fillMaxWidth().height(220.dp))
        }
    }
}

// -------------------------------------------------- список однієї літери

@Composable
private fun LetterList(
    letter: String,
    apps: List<AppInfo>,
    notifications: Map<String, AppNotification>,
    padH: androidx.compose.ui.unit.Dp,
    labelColor: Color,
    onLaunch: (AppInfo) -> Unit,
    onOpenNotification: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
    onDismissLetter: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val iconSize = spec.iconSizeDp.dp * LocalIconScale.current
    val showIcons = LocalIconsOverride.current ?: spec.showIcons
    val shadow = parseColor(theme.manifest.colors.labelShadow, Color.Transparent)
    val listState = rememberLazyListState()

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(letter) { detectTapGestures { onDismissLetter() } },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = PaddingValues(top = 96.dp, bottom = 80.dp, end = 44.dp),
        ) {
            item(key = "__letter__") {
                Text(
                    text = letter,
                    color = labelColor.copy(alpha = 0.55f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = padH, bottom = 6.dp),
                )
            }

            items(apps, key = { it.key }) { app ->
                val notification = notifications[app.packageName]
                val trailing: (@Composable () -> Unit)? = if (notification != null) {
                    {
                        NotificationDot(
                            count = notification.count,
                            color = MaterialTheme.colorScheme.primary,
                            textColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    null
                }
                Box(Modifier.padding(horizontal = padH)) {
                    SwipeableRow(
                        enabled = notification != null,
                        onSwipeRight = { onOpenNotification(app.packageName) },
                        onSwipeLeft = { onDismissNotification(app.packageName) },
                        hintColor = labelColor,
                    ) {
                        AppRow(
                            label = app.label,
                            labelColor = labelColor,
                            shadowColor = shadow,
                            iconSize = iconSize,
                            iconGap = spec.iconGapDp.dp,
                            labelSizeSp = spec.labelSizeSp,
                            labelWeight = spec.labelWeight,
                            allCaps = theme.manifest.typography.allCaps,
                            align = spec.align,
                            showIcon = showIcons,
                            modifier = Modifier
                                .padding(vertical = (spec.rowSpacingDp / 2).dp)
                                .pointerInput(app.key) {
                                    detectTapGestures { onLaunch(app) }
                                },
                            trailing = trailing,
                        ) {
                            AppIconImage(app.packageName, app.activityName, iconSize)
                        }
                    }
                }
            }

            if (apps.isEmpty()) {
                item {
                    Text(
                        "Немає додатків на цю літеру",
                        color = labelColor.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = padH),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ рядки

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRow(
    entry: HomeEntry,
    index: Int,
    total: Int,
    isExpanded: Boolean,
    notification: AppNotification?,
    onLaunch: (AppRef) -> Unit,
    onToggleFolder: () -> Unit,
    onAction: (HomeEntry, HomeRowAction) -> Unit,
    onOpenNotification: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val iconSize = spec.iconSizeDp.dp * LocalIconScale.current
    val showIcons = LocalIconsOverride.current ?: spec.showIcons
    val labelColor = parseColor(theme.manifest.colors.homeLabel, Color.White)
    val shadow = parseColor(theme.manifest.colors.labelShadow, Color.Transparent)

    var menuOpen by remember { mutableStateOf(false) }

    val trailing: (@Composable () -> Unit)? = if (notification != null) {
        {
            NotificationDot(
                count = notification.count,
                color = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    } else {
        null
    }

    Box {
        when (entry) {
            is AppEntry -> SwipeableRow(
                enabled = notification != null,
                onSwipeRight = { onOpenNotification(entry.app.packageName) },
                onSwipeLeft = { onDismissNotification(entry.app.packageName) },
                hintColor = labelColor,
            ) {
                AppRow(
                    label = entry.label,
                    labelColor = labelColor,
                    shadowColor = shadow,
                    iconSize = iconSize,
                    iconGap = spec.iconGapDp.dp,
                    labelSizeSp = spec.labelSizeSp,
                    labelWeight = spec.labelWeight,
                    allCaps = theme.manifest.typography.allCaps,
                    align = spec.align,
                    showIcon = showIcons,
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .combinedClickable(
                            onClick = { onLaunch(entry.app) },
                            onLongClick = { menuOpen = true },
                        ),
                    trailing = trailing,
                ) {
                    AppIconImage(entry.app.packageName, entry.app.activityName, iconSize)
                }
            }

            is FolderEntry -> AppRow(
                label = entry.name,
                labelColor = labelColor,
                shadowColor = shadow,
                iconSize = iconSize,
                iconGap = spec.iconGapDp.dp,
                labelSizeSp = spec.labelSizeSp,
                labelWeight = spec.labelWeight,
                allCaps = theme.manifest.typography.allCaps,
                align = spec.align,
                showIcon = showIcons,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .combinedClickable(
                        onClick = { onToggleFolder() },
                        onLongClick = { menuOpen = true },
                    ),
                trailing = {
                    Text(
                        text = if (isExpanded) "▾" else "▸",
                        color = labelColor.copy(alpha = 0.6f),
                    )
                },
            ) {
                FolderPreview(
                    entry.apps,
                    iconSize,
                    parseColor(theme.manifest.colors.surface, Color.DarkGray).copy(alpha = 0.55f),
                )
            }

            is WidgetEntry -> WidgetRow(entry) { menuOpen = true }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            HomeRowAction.entries.forEach { action ->
                val visible = when (action) {
                    HomeRowAction.MOVE_UP -> index > 0
                    HomeRowAction.MOVE_DOWN -> index < total - 1
                    HomeRowAction.MAKE_FOLDER -> entry is AppEntry
                    HomeRowAction.RENAME -> entry !is WidgetEntry
                    HomeRowAction.APP_INFO -> entry is AppEntry
                    HomeRowAction.REMOVE -> true
                }
                if (!visible) return@forEach
                DropdownMenuItem(
                    text = { Text(action.title) },
                    onClick = {
                        menuOpen = false
                        onAction(entry, action)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WidgetRow(entry: WidgetEntry, onLongClick: () -> Unit) {
    val controller = LocalWidgetController.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(entry.heightDp.dp)
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> controller.createView(ctx, entry.appWidgetId) ?: FrameLayout(ctx) },
            update = { view ->
                if (view is android.appwidget.AppWidgetHostView && view.width > 0 && view.height > 0) {
                    val density = view.resources.displayMetrics.density
                    val w = (view.width / density).toInt().coerceAtLeast(1)
                    val h = (view.height / density).toInt().coerceAtLeast(1)
                    runCatching { view.updateAppWidgetSize(android.os.Bundle(), w, h, w, h) }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChildren(
    folder: FolderEntry,
    onLaunch: (AppRef) -> Unit,
    onRemove: (AppRef) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val iconSize = spec.iconSizeDp.dp * LocalIconScale.current * 0.85f
    val showIcons = LocalIconsOverride.current ?: spec.showIcons
    val labelColor = parseColor(theme.manifest.colors.homeLabel, Color.White).copy(alpha = 0.9f)
    val shadow = parseColor(theme.manifest.colors.labelShadow, Color.Transparent)

    Column(Modifier.padding(start = 24.dp, top = 4.dp)) {
        folder.apps.forEach { ref ->
            var menu by remember(ref.key) { mutableStateOf(false) }
            Box {
                AppRow(
                    label = ref.label.ifBlank { ref.packageName },
                    labelColor = labelColor,
                    shadowColor = shadow,
                    iconSize = iconSize,
                    iconGap = spec.iconGapDp.dp,
                    labelSizeSp = spec.labelSizeSp * 0.92f,
                    labelWeight = spec.labelWeight,
                    allCaps = theme.manifest.typography.allCaps,
                    align = spec.align,
                    showIcon = showIcons,
                    modifier = Modifier
                        .padding(vertical = 5.dp)
                        .combinedClickable(
                            onClick = { onLaunch(ref) },
                            onLongClick = { menu = true },
                        ),
                ) {
                    AppIconImage(ref.packageName, ref.activityName, iconSize)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Прибрати з папки") },
                        onClick = { menu = false; onRemove(ref) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
