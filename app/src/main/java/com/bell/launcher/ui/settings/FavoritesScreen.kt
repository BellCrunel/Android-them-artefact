package com.bell.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.ui.components.AlphabetScrubber
import com.bell.launcher.ui.components.AppIconImage
import com.bell.launcher.util.IndexLetters
import kotlinx.coroutines.launch

private const val TOP_MARK = IndexLetters.FAVORITES

/**
 * Вибір додатків, які показуються на головному екрані —
 * список із чекбоксами й алфавітним скролом справа.
 */
@Composable
fun FavoritesScreen(
    apps: List<AppInfo>,
    favorites: Set<String>,
    onToggle: (AppInfo) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visible = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    val letters = remember(visible) {
        IndexLetters.sortLetters(
            listOf(TOP_MARK) + visible.map { IndexLetters.of(it.label) }.distinct()
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxSize()) {

                Text(
                    "Оберіть додатки",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
                )
                Text(
                    "Вони будуть у списку на головному екрані. " +
                        "Обрано: ${favorites.size}. Комфортно — 5–8.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Пошук") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 44.dp, bottom = 96.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.key }) { app ->
                            FavoriteRow(
                                app = app,
                                checked = app.key in favorites,
                                onToggle = { onToggle(app) },
                            )
                        }
                    }

                    if (letters.size > 2) {
                        AlphabetScrubber(
                            letters = letters,
                            letterColor = MaterialTheme.colorScheme.onBackground,
                            bubbleColor = MaterialTheme.colorScheme.primary,
                            bubbleTextColor = MaterialTheme.colorScheme.onPrimary,
                            onLetter = { letter ->
                                val index = if (letter == TOP_MARK) 0 else visible.indexOfFirst {
                                    IndexLetters.of(it.label) == letter
                                }
                                if (index >= 0) scope.launch { listState.scrollToItem(index) }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 6.dp),
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = onDone,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = "Готово")
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    app: AppInfo,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        AppIconImage(app.packageName, app.activityName, 34.dp)
        Spacer(Modifier.width(16.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}
