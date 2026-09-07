package com.bell.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bell.launcher.data.GestureAction
import com.bell.launcher.data.GestureSlot
import com.bell.launcher.data.LauncherSettings

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    themeName: String,
    appearanceSummary: String,
    hiddenCount: Int,
    favoritesCount: Int,
    weatherText: String,
    notificationsEnabled: Boolean,
    onNotificationAccess: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenFavorites: () -> Unit,
    onAddWidget: () -> Unit,
    onGesture: (GestureSlot, GestureAction) -> Unit,
    onWeatherEnabled: (Boolean) -> Unit,
    onUseLocation: (Boolean) -> Unit,
    onEditCity: () -> Unit,
    onRefreshWeather: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "Налаштування",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                SectionTitle("Вигляд")
                // Один вхід замість п'яти окремих пунктів: тема, фон, шпалери,
                // іконки й шрифт тепер живуть на спільному екрані з передпереглядом.
                RowItem(
                    title = "Вигляд лаунчера",
                    subtitle = "$themeName · $appearanceSummary",
                    onClick = onOpenAppearance,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Головний екран")
                RowItem(
                    title = "Обрані додатки",
                    subtitle = if (favoritesCount == 0) "Не обрано" else "$favoritesCount у списку",
                    onClick = onOpenFavorites,
                )
                RowItem(title = "Додати віджет", subtitle = "Обрати зі списку системи", onClick = onAddWidget)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Сповіщення")
                RowItem(
                    title = "Доступ до сповіщень",
                    subtitle = if (notificationsEnabled) {
                        "Увімкнено. Свайп рядка вправо — відкрити, вліво — прибрати"
                    } else {
                        "Вимкнено. Натисніть, щоб дозволити в системних налаштуваннях"
                    },
                    onClick = onNotificationAccess,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Погода")
                SwitchItem("Показувати погоду", settings.weatherEnabled, onWeatherEnabled)
                SwitchItem("Брати з геолокації", settings.useLocation, onUseLocation)
                RowItem(
                    title = "Місто вручну",
                    subtitle = settings.manualCity.ifBlank {
                        "Не задано — беремо з геолокації або з часового поясу"
                    },
                    onClick = onEditCity,
                )
                RowItem(title = "Оновити зараз", subtitle = weatherText, onClick = onRefreshWeather)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Жести")
                GestureItem(GestureSlot.SWIPE_UP, settings.swipeUp, onGesture)
                GestureItem(GestureSlot.SWIPE_DOWN, settings.swipeDown, onGesture)
                GestureItem(GestureSlot.TWO_FINGER_SWIPE_DOWN, settings.twoFingerSwipeDown, onGesture)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Додатки")
                RowItem(
                    title = "Приховані додатки",
                    subtitle = if (hiddenCount == 0) "Немає" else "$hiddenCount прихованих",
                    onClick = onOpenHidden,
                )

                Spacer(Modifier.height(28.dp))
                Text(
                    "Artefact Launcher · формат тем v2",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun RowItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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

/** Рядок із випадним списком варіантів. */
@Composable
private fun <T> ChoiceItem(
    title: String,
    current: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    current,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchItem(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GestureItem(
    slot: GestureSlot,
    current: GestureAction,
    onGesture: (GestureSlot, GestureAction) -> Unit,
) {
    ChoiceItem(
        title = slot.title,
        current = current.title,
        options = GestureAction.entries.map { it.title to it },
        onSelect = { onGesture(slot, it) },
    )
}
