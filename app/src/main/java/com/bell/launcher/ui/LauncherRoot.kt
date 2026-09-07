package com.bell.launcher.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bell.launcher.data.GestureAction
import com.bell.launcher.data.model.AppEntry
import com.bell.launcher.data.model.AppRef
import com.bell.launcher.data.model.FolderEntry
import com.bell.launcher.data.model.HomeEntry
import com.bell.launcher.data.model.WidgetEntry
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.ui.components.LocalIconLoader
import com.bell.launcher.ui.components.LocalIconScale
import com.bell.launcher.ui.components.LocalIconsOverride
import com.bell.launcher.ui.components.ThemeWallpaper
import com.bell.launcher.ui.drawer.AppDrawer
import com.bell.launcher.ui.folder.RenameDialog
import com.bell.launcher.ui.gestures.launcherGestures
import com.bell.launcher.ui.home.HomeRowAction
import com.bell.launcher.ui.home.HomeScreen
import com.bell.launcher.ui.settings.HiddenAppsScreen
import com.bell.launcher.ui.settings.SettingsScreen
import com.bell.launcher.ui.settings.ThemeGallery
import com.bell.launcher.ui.widget.LocalWidgetController

@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel,
    onImportTheme: () -> Unit,
    onRequestLocationPermission: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val overlay by viewModel.overlay.collectAsState()
    val weather by viewModel.weather.collectAsState()

    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val widgets = LocalWidgetController.current

    val listState = rememberLazyListState()
    val scrollFraction by remember {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex.toFloat()
            val offset = listState.firstVisibleItemScrollOffset / 1000f
            ((index + offset) / 8f).coerceIn(0f, 1f)
        }
    }

    var homeMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<HomeEntry?>(null) }
    var cityDialog by remember { mutableStateOf(false) }

    val folders = state.layout.sorted.filterIsInstance<FolderEntry>()

    fun launch(ref: AppRef) {
        viewModel.appRepository.launch(ref.packageName, ref.activityName)
    }

    fun addWidget() {
        widgets.pickWidget { id, info ->
            val height = (info.minHeight / context.resources.displayMetrics.density)
                .toInt().coerceIn(80, 360)
            viewModel.addWidget(id, height, info.provider.packageName)
        }
    }

    fun runAction(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_DRAWER -> viewModel.openOverlay(Overlay.DRAWER)
            GestureAction.EXPAND_NOTIFICATIONS -> expandNotifications(context)
            GestureAction.OPEN_SETTINGS -> viewModel.openOverlay(Overlay.SETTINGS)
            GestureAction.OPEN_THEMES -> viewModel.openOverlay(Overlay.THEMES)
            GestureAction.OPEN_WIDGETS -> addWidget()
        }
    }

    fun handleRowAction(entry: HomeEntry, action: HomeRowAction) {
        val entries = state.layout.sorted
        val index = entries.indexOfFirst { it.id == entry.id }
        when (action) {
            HomeRowAction.MOVE_UP -> if (index > 0) viewModel.moveEntry(index, index - 1)
            HomeRowAction.MOVE_DOWN -> if (index in 0 until entries.size - 1) viewModel.moveEntry(index, index + 1)
            HomeRowAction.RENAME -> renameTarget = entry
            HomeRowAction.MAKE_FOLDER -> viewModel.makeFolder(entry.id)
            HomeRowAction.REMOVE -> {
                if (entry is WidgetEntry) widgets.delete(entry.appWidgetId)
                viewModel.removeEntry(entry.id)
            }
            HomeRowAction.APP_INFO -> (entry as? AppEntry)?.let {
                viewModel.appRepository.openAppInfo(it.app.packageName, it.app.activityName)
            }
        }
    }

    CompositionLocalProvider(
        LocalIconLoader provides viewModel.iconLoader,
        LocalIconScale provides state.settings.iconScale,
        LocalIconsOverride provides state.settings.showIconsOverride,
    ) {
        Box(Modifier.fillMaxSize()) {

            ThemeWallpaper(theme = theme, scrollFraction = scrollFraction)

            HomeScreen(
                state = state,
                weather = weather,
                listState = listState,
                onLaunch = ::launch,
                onAction = ::handleRowAction,
                onFolderAppRemove = { folderId, ref -> viewModel.removeFromFolder(folderId, ref) },
                onLongPressEmpty = { homeMenu = true },
                modifier = Modifier.launcherGestures(
                    onSwipeUp = { if (overlay == Overlay.NONE) runAction(state.settings.swipeUp) },
                    onSwipeDown = { if (overlay == Overlay.NONE) runAction(state.settings.swipeDown) },
                    onTwoFingerSwipeDown = {
                        if (overlay == Overlay.NONE) runAction(state.settings.twoFingerSwipeDown)
                    },
                ),
            )

            AnimatedVisibility(visible = homeMenu, enter = fadeIn(), exit = fadeOut()) {
                HomeMenu(
                    onWidgets = { homeMenu = false; addWidget() },
                    onWallpaper = { homeMenu = false; pickWallpaper(context) },
                    onThemes = { homeMenu = false; viewModel.openOverlay(Overlay.THEMES) },
                    onSettings = { homeMenu = false; viewModel.openOverlay(Overlay.SETTINGS) },
                    onDismiss = { homeMenu = false },
                )
            }

            AnimatedVisibility(
                visible = overlay == Overlay.DRAWER,
                enter = slideInVertically(animationSpec = tween(260)) { it } + fadeIn(),
                exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(),
            ) {
                AppDrawer(
                    apps = state.apps,
                    hiddenApps = state.settings.hiddenApps,
                    folders = folders,
                    onLaunch = { viewModel.appRepository.launch(it.packageName, it.activityName) },
                    onAddToHome = { app ->
                        viewModel.addAppToHome(app)
                        viewModel.closeOverlay()
                    },
                    onAddToFolder = { folderId, app ->
                        viewModel.addToFolder(folderId, app.toRef())
                        viewModel.closeOverlay()
                    },
                    onAppInfo = { viewModel.appRepository.openAppInfo(it.packageName, it.activityName) },
                    onUninstall = { viewModel.appRepository.uninstall(it.packageName) },
                    onHide = { viewModel.toggleHidden(it.key) },
                    onClose = { viewModel.closeOverlay() },
                )
            }

            AnimatedVisibility(visible = overlay == Overlay.SETTINGS, enter = fadeIn(), exit = fadeOut()) {
                SettingsScreen(
                    settings = state.settings,
                    themeName = theme.name,
                    hiddenCount = state.settings.hiddenApps.size,
                    weatherText = weather?.let { "${it.shortText}  ${it.place}".trim() } ?: "Немає даних",
                    onOpenThemes = { viewModel.openOverlay(Overlay.THEMES) },
                    onOpenHidden = { viewModel.openOverlay(Overlay.HIDDEN_APPS) },
                    onAddWidget = { viewModel.closeOverlay(); addWidget() },
                    onChangeWallpaper = { pickWallpaper(context) },
                    onGesture = { slot, action -> viewModel.setGesture(slot, action) },
                    onIconScale = { viewModel.setIconScale(it) },
                    onIcons = { viewModel.setIconsOverride(it) },
                    onWeatherEnabled = { viewModel.setWeatherEnabled(it) },
                    onUseLocation = { enabled ->
                        if (enabled && !viewModel.hasLocationPermission()) onRequestLocationPermission()
                        viewModel.setUseLocation(enabled)
                    },
                    onEditCity = { cityDialog = true },
                    onRefreshWeather = { viewModel.refreshWeather(force = true) },
                    onBack = { viewModel.closeOverlay() },
                )
            }

            AnimatedVisibility(visible = overlay == Overlay.THEMES, enter = fadeIn(), exit = fadeOut()) {
                ThemeGallery(
                    themes = state.themes,
                    currentId = theme.id,
                    onApply = { viewModel.setTheme(it) },
                    onDelete = { viewModel.deleteTheme(it) },
                    onImport = onImportTheme,
                    onBack = { viewModel.openOverlay(Overlay.SETTINGS) },
                )
            }

            AnimatedVisibility(visible = overlay == Overlay.HIDDEN_APPS, enter = fadeIn(), exit = fadeOut()) {
                HiddenAppsScreen(
                    apps = state.apps,
                    hidden = state.settings.hiddenApps,
                    onToggle = { viewModel.toggleHidden(it) },
                    onBack = { viewModel.openOverlay(Overlay.SETTINGS) },
                )
            }
        }
    }

    renameTarget?.let { entry ->
        val initial = when (entry) {
            is AppEntry -> entry.label
            is FolderEntry -> entry.name
            else -> ""
        }
        RenameDialog(
            title = "Назва",
            initial = initial,
            onConfirm = { value ->
                viewModel.renameEntry(entry.id, value)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (cityDialog) {
        RenameDialog(
            title = "Місто для погоди",
            initial = state.settings.manualCity,
            placeholder = "Напр. Київ",
            onConfirm = { city ->
                viewModel.setCity(city)
                cityDialog = false
            },
            onDismiss = { cityDialog = false },
        )
    }

    BackHandler(enabled = overlay != Overlay.NONE || homeMenu || renameTarget != null || cityDialog) {
        when {
            cityDialog -> cityDialog = false
            renameTarget != null -> renameTarget = null
            homeMenu -> homeMenu = false
            overlay == Overlay.HIDDEN_APPS || overlay == Overlay.THEMES -> viewModel.openOverlay(Overlay.SETTINGS)
            else -> viewModel.closeOverlay()
        }
    }
}

@Composable
private fun HomeMenu(
    onWidgets: () -> Unit,
    onWallpaper: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(Icons.Default.Widgets, "Віджети", onWidgets)
            MenuButton(Icons.Default.Wallpaper, "Шпалери", onWallpaper)
            MenuButton(Icons.Default.Palette, "Теми", onThemes)
            MenuButton(Icons.Default.Settings, "Налаштування", onSettings)
        }
    }
}

@Composable
private fun MenuButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(86.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun pickWallpaper(context: Context) {
    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "Обрати шпалери").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Розгортає шторку сповіщень (працює не на всіх прошивках). */
private fun expandNotifications(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar")
        val clazz = Class.forName("android.app.StatusBarManager")
        val method = clazz.getMethod("expandNotificationsPanel")
        method.invoke(service)
    }
}
