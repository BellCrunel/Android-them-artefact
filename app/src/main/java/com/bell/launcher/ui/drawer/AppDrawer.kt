package com.bell.launcher.ui.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.data.model.FolderEntry
import com.bell.launcher.theme.LocalLauncherTheme
import com.bell.launcher.theme.parseColor
import com.bell.launcher.ui.components.AlphabetScrubber
import com.bell.launcher.ui.components.AppIconImage
import com.bell.launcher.ui.components.AppRow
import com.bell.launcher.ui.components.LocalIconScale
import com.bell.launcher.ui.components.LocalIconsOverride
import com.bell.launcher.util.IndexLetters
import kotlinx.coroutines.launch

private const val TOP_MARK = IndexLetters.FAVORITES

@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    hiddenApps: Set<String>,
    folders: List<FolderEntry>,
    onLaunch: (AppInfo) -> Unit,
    onAddToHome: (AppInfo) -> Unit,
    onAddToFolder: (String, AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onHide: (AppInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focusSearch: Boolean = true,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val effects = theme.manifest.effects
    var query by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Свайп угору відкриває шухляду вже з активним пошуком — можна одразу друкувати.
    LaunchedEffect(Unit) {
        if (focusSearch) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val visible = remember(apps, hiddenApps, query) {
        apps.filter { it.key !in hiddenApps }
            .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    }

    val letters = remember(visible) {
        IndexLetters.sortLetters(
            listOf(TOP_MARK) + visible.map { IndexLetters.of(it.label) }.distinct()
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                parseColor(theme.manifest.colors.background, Color.Black)
                    .copy(alpha = effects.drawerScrim.coerceIn(0.2f, 1f))
            )
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Пошук додатків…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.clickable { if (query.isEmpty()) onClose() else query = "" },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .focusRequester(focusRequester),
            )

            Box(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нічого не знайдено", color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = spec.horizontalPaddingDp.dp,
                            end = if (spec.drawerScrubber) 44.dp else spec.horizontalPaddingDp.dp,
                            top = 4.dp,
                            bottom = 40.dp,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.key }) { app ->
                            DrawerRow(
                                app = app,
                                folders = folders,
                                onLaunch = { onLaunch(app) },
                                onAddToHome = { onAddToHome(app) },
                                onAddToFolder = { folderId -> onAddToFolder(folderId, app) },
                                onAppInfo = { onAppInfo(app) },
                                onUninstall = { onUninstall(app) },
                                onHide = { onHide(app) },
                            )
                        }
                    }

                    if (spec.drawerScrubber && letters.size > 2) {
                        AlphabetScrubber(
                            letters = letters,
                            letterColor = parseColor(theme.manifest.colors.scrubber, Color.White),
                            bubbleColor = MaterialTheme.colorScheme.primary,
                            bubbleTextColor = MaterialTheme.colorScheme.onPrimary,
                            onLetter = { letter ->
                                val index = if (letter == TOP_MARK) {
                                    0
                                } else {
                                    visible.indexOfFirst { IndexLetters.of(it.label) == letter }
                                }
                                if (index >= 0) {
                                    scope.launch { listState.scrollToItem(index) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    app: AppInfo,
    folders: List<FolderEntry>,
    onLaunch: () -> Unit,
    onAddToHome: () -> Unit,
    onAddToFolder: (String) -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onHide: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val spec = theme.manifest.layout
    val iconSize = spec.iconSizeDp.dp * LocalIconScale.current
    val showIcons = LocalIconsOverride.current ?: spec.drawerShowIcons
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        AppRow(
            label = app.label,
            labelColor = parseColor(theme.manifest.colors.drawerLabel, Color.White),
            shadowColor = Color.Transparent,
            iconSize = iconSize,
            iconGap = spec.iconGapDp.dp,
            labelSizeSp = spec.labelSizeSp,
            labelWeight = spec.labelWeight,
            allCaps = theme.manifest.typography.allCaps,
            align = spec.align,
            showIcon = showIcons,
            modifier = Modifier
                .padding(vertical = (spec.rowSpacingDp / 2).dp)
                .combinedClickable(onClick = onLaunch, onLongClick = { menuOpen = true }),
        ) {
            AppIconImage(app.packageName, app.activityName, iconSize)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("На головний екран") },
                onClick = { menuOpen = false; onAddToHome() },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text("У папку «${folder.name}»") },
                    onClick = { menuOpen = false; onAddToFolder(folder.id) },
                )
            }
            DropdownMenuItem(
                text = { Text("Про додаток") },
                onClick = { menuOpen = false; onAppInfo() },
            )
            DropdownMenuItem(
                text = { Text("Сховати з шухляди") },
                onClick = { menuOpen = false; onHide() },
            )
            if (!app.system) {
                DropdownMenuItem(
                    text = { Text("Видалити") },
                    onClick = { menuOpen = false; onUninstall() },
                )
            }
        }
    }
}
