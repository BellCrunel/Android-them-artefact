package com.bell.launcher.ui.home

import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bell.launcher.data.AppNotification
import com.bell.launcher.data.HomeOrder
import com.bell.launcher.data.LauncherSettings
import com.bell.launcher.data.Weather
import com.bell.launcher.data.model.AppEntry
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.data.model.AppRef
import com.bell.launcher.data.model.FolderEntry
import com.bell.launcher.data.model.HomeEntry
import com.bell.launcher.data.model.WidgetEntry
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.theme.model.AlignMode
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

/** Внутрішній відступ планки під списком — однаковий з обох боків. */
private val PLATE_PAD = 14.dp
private val PLATE_CORNER = 20.dp

/** Висота, на якій рядки розчиняються, входячи під прибитий годинник. */
private val FADE_HEIGHT = 52.dp

/**
 * Чи може зараз гортатися видимий список.
 *
 * Потрібно, щоб жести лаунчера не змагалися з прокруткою: поки список має куди
 * гортатися, свайп угору його гортає, і лише в самому кінці відкриває пошук.
 */
@Stable
class HomeScrollState {
    var canScrollForward by mutableStateOf(false)
    var canScrollBackward by mutableStateOf(false)

    fun reset() {
        canScrollForward = false
        canScrollBackward = false
    }
}

/**
 * Усе, що залежить від сторони екрана, зібрано в одному місці.
 * Дзеркало перевертає розкладку цілком, тож кожен елемент має питати сторону
 * тут, а не рахувати її самостійно — інакше при наступній правці одне місце
 * оновлять, а друге забудуть.
 */
@Stable
private data class SideLayout(
    val mirrored: Boolean,
    val rowAlign: AlignMode,
    val headerAlign: AlignMode,
) {
    /** Смуга алфавіту. */
    val railAlignment: Alignment.Horizontal
        get() = if (mirrored) Alignment.Start else Alignment.End

    /** Показник літери стоїть навпроти годинника, з протилежного краю. */
    val indicatorAlignment: Alignment
        get() = if (headerAlign == AlignMode.END) Alignment.TopStart else Alignment.TopEnd

    /** Відступ списку під смугу алфавіту. */
    fun listPadding(rail: Dp): PaddingValues =
        if (mirrored) PaddingValues(start = rail) else PaddingValues(end = rail)
}

@Composable
fun HomeScreen(
    state: LauncherUiState,
    weather: Weather?,
    listState: LazyListState,
    scrollState: HomeScrollState,
    notifications: Map<String, AppNotification>,
    usageScores: Map<String, Float>,
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
    val header = theme.manifest.header
    val labelColor = parseColor(theme.manifest.colors.homeLabel, Color.White)
    val padH = spec.horizontalPaddingDp.dp

    val mirrored = state.settings.mirrored
    val side = remember(mirrored, spec.align, header.align) {
        SideLayout(
            mirrored = mirrored,
            rowAlign = mirror(spec.align, mirrored),
            headerAlign = mirror(header.align, mirrored),
        )
    }

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
    var railDragging by remember { mutableStateOf(false) }

    // Висоту прибитого заголовка міряємо, а не рахуємо з теми: масштаб шрифта
    // в системних налаштуваннях зсуває її на десятки пікселів.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val fadePx = with(density) { FADE_HEIGHT.toPx() }

    val plateColor = parseColor(theme.manifest.colors.surface, Color.Black)
        .copy(alpha = state.settings.plateAlpha)
    val plateOn = state.settings.plateAlpha > 0.01f

    Box(modifier.fillMaxSize()) {

        // Crossfade перемикається лише між «обрані» і «режим літери».
        // Раніше targetState був самою літерою — тому кожна нова літера
        // під пальцем запускала повну анімацію переходу, і саме це лагало.
        val letterMode = activeLetter != null && activeLetter != IndexLetters.FAVORITES

        Crossfade(
            targetState = letterMode,
            animationSpec = tween(140),
            label = "homeMode",
            modifier = Modifier
                .fillMaxSize()
                // Маска, а не підкладка кольором: підкладка сховала б шпалери.
                // Offscreen обов'язковий, інакше DstIn з'їсть усе, що намальовано
                // нижче по дереву, разом зі шпалерами.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (headerHeightPx > 0) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black),
                                startY = (headerHeightPx - fadePx).coerceAtLeast(0f),
                                endY = headerHeightPx.toFloat().coerceAtLeast(1f),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        ) { inLetterMode ->
            if (!inLetterMode) {
                FavoritesList(
                    state = state,
                    listState = listState,
                    scrollState = scrollState,
                    side = side,
                    usageScores = usageScores,
                    topInset = headerHeight,
                    plateColor = plateColor,
                    plateOn = plateOn,
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
                val letter = activeLetter ?: IndexLetters.OTHER
                LetterList(
                    letter = letter,
                    apps = grouped[letter].orEmpty(),
                    scrollState = scrollState,
                    side = side,
                    settings = state.settings,
                    topInset = headerHeight,
                    plateColor = plateColor,
                    plateOn = plateOn,
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

        // Прибитий заголовок. Малюється ПІСЛЯ списку, тож рядки їдуть під нього.
        Box(
            Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .onGloballyPositioned { headerHeightPx = it.size.height }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPressEmpty() }) }
                .padding(horizontal = padH),
        ) {
            ClockHeader(weather = weather, align = side.headerAlign)
        }

        // Показник поточної літери — навпроти годинника, далеко від долоні.
        LetterIndicator(
            letter = activeLetter,
            visible = railDragging && activeLetter != null,
            topOffset = indicatorTop(header.paddingTopDp, header.clockSizeSp),
            modifier = Modifier.align(side.indicatorAlignment).systemBarsPadding(),
        )

        LetterRail(
            letters = letters,
            active = activeLetter,
            color = parseColor(theme.manifest.colors.scrubber, Color.White),
            onActiveChange = { activeLetter = it },
            onRelease = { /* лишаємо літеру, щоб можна було натиснути додаток */ },
            onDraggingChange = { railDragging = it },
            onLeft = side.mirrored,
            feedback = state.settings.railFeedback,
            modifier = Modifier
                .align(
                    if (side.mirrored) Alignment.BottomStart else Alignment.BottomEnd
                )
                .systemBarsPadding()
                .padding(
                    start = if (side.mirrored) 2.dp else 0.dp,
                    end = if (side.mirrored) 0.dp else 2.dp,
                    bottom = 64.dp,
                ),
        )
    }
}

/** Дзеркалить вирівнювання. Центр дзеркальний сам до себе — його не чіпаємо. */
private fun mirror(align: AlignMode, mirrored: Boolean): AlignMode = when {
    align == AlignMode.CENTER -> AlignMode.CENTER
    !mirrored -> align
    align == AlignMode.START -> AlignMode.END
    else -> AlignMode.START
}

/** Вертикальна позиція показника — по центру цифр годинника. */
private fun indicatorTop(paddingTopDp: Int, clockSizeSp: Float): Dp =
    (paddingTopDp + clockSizeSp * 0.6f - 32f).coerceAtLeast(0f).dp

// ------------------------------------------------------------ обрані додатки

@Composable
private fun FavoritesList(
    state: LauncherUiState,
    listState: LazyListState,
    scrollState: HomeScrollState,
    side: SideLayout,
    usageScores: Map<String, Float>,
    topInset: Dp,
    plateColor: Color,
    plateOn: Boolean,
    notifications: Map<String, AppNotification>,
    padH: Dp,
    onLaunch: (AppRef) -> Unit,
    onAction: (HomeEntry, HomeRowAction) -> Unit,
    onFolderAppRemove: (String, AppRef) -> Unit,
    onLongPressEmpty: () -> Unit,
    onOpenNotification: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val expanded = rememberExpandedFolders()

    val order = state.settings.homeOrder
    val entries = remember(state.layout.sorted, order, usageScores) {
        reorder(state.layout.sorted, order, usageScores)
    }

    val showIcons = LocalIconsOverride.current ?: spec.showIcons
    val showLabels = state.settings.showLabels
    val iconSize = spec.iconSizeDp.dp * LocalIconScale.current

    val plateWidth = rememberPlateWidth(
        labels = entries.map { entryLabel(it) },
        showIcons = showIcons,
        showLabels = showLabels,
        iconSize = iconSize,
        iconGap = spec.iconGapDp.dp,
        labelSizeSp = spec.labelSizeSp,
        labelWeight = spec.labelWeight,
        enabled = plateOn,
    )
    val plateMargin = (padH - PLATE_PAD).coerceAtLeast(0.dp)

    val emptyAreaModifier = Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { onLongPressEmpty() })
    }

    // Головний екран за замовчуванням не гортається: інакше свайп угору
    // (жест відкриття пошуку) піднімав би список без потреби.
    // Але якщо обраних більше, ніж влазить у екран, гортання вмикається —
    // інакше до нижніх додатків просто не дістатися.
    var overflows by remember(entries.size) { mutableStateOf(false) }
    LaunchedEffect(entries.size, listState) {
        snapshotFlow { listState.layoutInfo }.collect { info ->
            if (overflows) return@collect
            // Останній «справжній» рядок — перед службовим хвостом __tail__.
            val lastRealIndex = info.totalItemsCount - 2
            if (lastRealIndex < 0) return@collect
            val item = info.visibleItemsInfo.firstOrNull { it.index == lastRealIndex }
            overflows = item == null || item.offset + item.size > info.viewportEndOffset
        }
    }

    LaunchedEffect(overflows) { if (!overflows) scrollState.reset() }
    LaunchedEffect(overflows, listState) {
        if (!overflows) return@LaunchedEffect
        snapshotFlow { listState.canScrollForward to listState.canScrollBackward }
            .collect { (forward, backward) ->
                scrollState.canScrollForward = forward
                scrollState.canScrollBackward = backward
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = side.listPadding(RAIL_WIDTH),
        userScrollEnabled = overflows,
    ) {
        item(key = "__top__") {
            // Місце під прибитий годинник: він більше не елемент списку.
            Box(emptyAreaModifier.fillMaxWidth().height(topInset))
        }

        if (entries.isEmpty()) {
            item(key = "__empty__") {
                Column(emptyAreaModifier.fillMaxWidth().padding(horizontal = padH)) {
                    Text(
                        text = "Порожньо. Проведіть пальцем по алфавіту,\n" +
                            "або довгий тап → Налаштування → Обрані додатки.",
                        color = parseColor(theme.manifest.colors.homeLabel, Color.White)
                            .copy(alpha = 0.7f),
                    )
                }
            }
        }

        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            PlateSegment(
                plateColor = plateColor,
                // Віджет малюється на всю ширину — заганяти його в планку,
                // розраховану по довжині назв, безглуздо.
                plateWidth = if (entry is WidgetEntry) null else plateWidth,
                plateMargin = plateMargin,
                side = side,
                first = index == 0,
                last = index == entries.size - 1,
                fallbackPadding = padH,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HomeRow(
                        entry = entry,
                        index = index,
                        total = entries.size,
                        align = side.rowAlign,
                        showLabels = showLabels,
                        manualOrder = order == HomeOrder.MANUAL,
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
                            align = side.rowAlign,
                            showLabels = showLabels,
                            onLaunch = onLaunch,
                            onRemove = { ref -> onFolderAppRemove(entry.id, ref) },
                        )
                    }

                    Spacer(Modifier.height(spec.rowSpacingDp.dp))
                }
            }
        }

        item(key = "__tail__") {
            // Порожня зона для довгого тапу. Коли список і так довший за екран,
            // тримати тут 220 dp порожнечі немає сенсу — це зайве гортання.
            Box(emptyAreaModifier.fillMaxWidth().height(if (overflows) 24.dp else 220.dp))
        }
    }
}

/**
 * Порядок рядків. Додатки пересортовуються **на своїх місцях**: віджети й папки
 * лишаються там, куди їх поставив користувач, інакше віджет-годинник поїхав би
 * в кінець списку при першому ж перемиканні режиму.
 */
private fun reorder(
    entries: List<HomeEntry>,
    order: HomeOrder,
    scores: Map<String, Float>,
): List<HomeEntry> {
    if (order == HomeOrder.MANUAL) return entries
    val slots = entries.indices.filter { entries[it] is AppEntry }
    if (slots.size < 2) return entries

    val apps = slots.map { entries[it] as AppEntry }
    val sorted = when (order) {
        HomeOrder.FREQUENCY -> apps.sortedWith(
            compareByDescending<AppEntry> { scores[it.app.key] ?: 0f }
                .thenBy { it.label.lowercase() }
        )
        HomeOrder.ALPHABET -> apps.sortedBy { it.label.lowercase() }
        HomeOrder.MANUAL -> apps
    }

    val result = entries.toMutableList()
    slots.forEachIndexed { i, slot -> result[slot] = sorted[i] }
    return result
}

private fun entryLabel(entry: HomeEntry): String = when (entry) {
    is AppEntry -> entry.label
    is FolderEntry -> entry.name
    else -> ""
}

// -------------------------------------------------- список однієї літери

@Composable
private fun LetterList(
    letter: String,
    apps: List<AppInfo>,
    scrollState: HomeScrollState,
    side: SideLayout,
    settings: LauncherSettings,
    topInset: Dp,
    plateColor: Color,
    plateOn: Boolean,
    notifications: Map<String, AppNotification>,
    padH: Dp,
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
    val showLabels = settings.showLabels
    val shadow = parseColor(theme.manifest.colors.labelShadow, Color.Transparent)
    val listState = rememberLazyListState()

    val plateWidth = rememberPlateWidth(
        labels = apps.map { it.label },
        showIcons = showIcons,
        showLabels = showLabels,
        iconSize = iconSize,
        iconGap = spec.iconGapDp.dp,
        labelSizeSp = spec.labelSizeSp,
        labelWeight = spec.labelWeight,
        enabled = plateOn,
    )
    val plateMargin = (padH - PLATE_PAD).coerceAtLeast(0.dp)

    // Нова літера — список починається згори, без анімації прокрутки
    LaunchedEffect(letter) { listState.scrollToItem(0) }

    // Повідомляємо назовні, чи є куди гортати: поки є, свайп угору гортає
    // список, а не відкриває пошук.
    DisposableEffect(Unit) { onDispose { scrollState.reset() } }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward to listState.canScrollBackward }
            .collect { (forward, backward) ->
                scrollState.canScrollForward = forward
                scrollState.canScrollBackward = backward
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(letter) { detectTapGestures { onDismissLetter() } },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            contentPadding = side.listPadding(RAIL_WIDTH),
        ) {
            item(key = "__top__") {
                Box(Modifier.fillMaxWidth().height(topInset))
            }

            item(key = "__letter__") {
                Box(Modifier.fillMaxWidth().padding(horizontal = padH)) {
                    Text(
                        text = letter,
                        color = labelColor.copy(alpha = 0.55f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(
                                if (side.rowAlign == AlignMode.END) {
                                    Alignment.CenterEnd
                                } else {
                                    Alignment.CenterStart
                                }
                            )
                            .padding(bottom = 6.dp),
                    )
                }
            }

            itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
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
                PlateSegment(
                    plateColor = plateColor,
                    plateWidth = plateWidth,
                    plateMargin = plateMargin,
                    side = side,
                    first = index == 0,
                    last = index == apps.size - 1,
                    fallbackPadding = padH,
                ) {
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
                            align = side.rowAlign,
                            showIcon = showIcons,
                            showLabel = showLabels,
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

            item(key = "__bottom__") { Box(Modifier.height(80.dp)) }
        }
    }
}

// ------------------------------------------------------------------ планка

/**
 * Ширина вмісту найширшого рядка.
 *
 * Міряємо ВСІ назви одразу через [rememberTextMeasurer], а не беремо ширину
 * з розкладки: `LazyColumn` компонує лише видимі рядки, тож при гортанні
 * до довшої назви планка стрибала б ушир.
 */
@Composable
private fun rememberPlateWidth(
    labels: List<String>,
    showIcons: Boolean,
    showLabels: Boolean,
    iconSize: Dp,
    iconGap: Dp,
    labelSizeSp: Float,
    labelWeight: Int,
    enabled: Boolean,
): Dp? {
    // Усі remember викликаються безумовно: composable не можна пропускати
    // за if, інакше при вмиканні планки Compose втратить слоти й упаде.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val style = remember(labelSizeSp, labelWeight) {
        TextStyle(
            fontSize = labelSizeSp.sp,
            fontWeight = FontWeight(labelWeight.coerceIn(100, 900)),
        )
    }
    val width = remember(labels, showIcons, showLabels, iconSize, iconGap, style, screenWidth) {
        with(density) {
            var content = 0.dp
            if (showIcons) content += iconSize
            if (showLabels && labels.isNotEmpty()) {
                val widest = labels.maxOf { measurer.measure(it, style).size.width }.toDp()
                if (showIcons) content += iconGap
                content += widest
            }
            // Довгі назви й так обрізаються трикрапкою — планка через пів екрана
            // виглядала б безглуздо.
            PLATE_PAD * 2 + content.coerceAtMost(screenWidth * 0.75f)
        }
    }
    return if (enabled) width else null
}

/**
 * Один сегмент планки під рядком. Сегменти стикаються впритул, тож виглядають
 * як одна суцільна смуга; скруглення — лише на самих краях списку.
 */
@Composable
private fun PlateSegment(
    plateColor: Color,
    plateWidth: Dp?,
    plateMargin: Dp,
    side: SideLayout,
    first: Boolean,
    last: Boolean,
    fallbackPadding: Dp,
    content: @Composable () -> Unit,
) {
    if (plateWidth == null) {
        Box(Modifier.fillMaxWidth().padding(horizontal = fallbackPadding)) { content() }
        return
    }
    val shape = RoundedCornerShape(
        topStart = if (first) PLATE_CORNER else 0.dp,
        topEnd = if (first) PLATE_CORNER else 0.dp,
        bottomStart = if (last) PLATE_CORNER else 0.dp,
        bottomEnd = if (last) PLATE_CORNER else 0.dp,
    )
    Box(
        Modifier.fillMaxWidth().padding(horizontal = plateMargin),
        contentAlignment = if (side.mirrored) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .width(plateWidth)
                .clip(shape)
                .background(plateColor)
                .padding(horizontal = PLATE_PAD),
        ) {
            content()
        }
    }
}

// ------------------------------------------------------------- показник літери

@Composable
private fun LetterIndicator(
    letter: String?,
    visible: Boolean,
    topOffset: Dp,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && letter != null,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(120)),
        modifier = modifier.padding(top = topOffset, start = 16.dp, end = 16.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
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
    align: AlignMode,
    showLabels: Boolean,
    manualOrder: Boolean,
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
                    align = align,
                    showIcon = showIcons,
                    showLabel = showLabels,
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
                align = align,
                showIcon = showIcons,
                showLabel = showLabels,
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
                    // У режимі сортування пункти переміщення нічого не роблять —
                    // ховаємо їх, щоб не виглядали зламаними.
                    HomeRowAction.MOVE_UP -> manualOrder && index > 0
                    HomeRowAction.MOVE_DOWN -> manualOrder && index < total - 1
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
    align: AlignMode,
    showLabels: Boolean,
    onLaunch: (AppRef) -> Unit,
    onRemove: (AppRef) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val iconSize = spec.iconSizeDp.dp * LocalIconScale.current * 0.85f
    val showIcons = LocalIconsOverride.current ?: spec.showIcons
    val labelColor = parseColor(theme.manifest.colors.homeLabel, Color.White).copy(alpha = 0.9f)
    val shadow = parseColor(theme.manifest.colors.labelShadow, Color.Transparent)

    val inset = if (align == AlignMode.END) {
        Modifier.padding(end = 24.dp, top = 4.dp)
    } else {
        Modifier.padding(start = 24.dp, top = 4.dp)
    }

    Column(inset) {
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
                    align = align,
                    showIcon = showIcons,
                    showLabel = showLabels,
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
