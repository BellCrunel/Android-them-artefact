package com.bell.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bell.launcher.data.WallpaperItem

/**
 * Галерея зображень із папки assets/wallpapers.
 * Якщо папка порожня — пояснюємо, куди класти файли.
 */
@Composable
fun WallpaperScreen(
    items: List<WallpaperItem>,
    selected: String?,
    dim: Float,
    loadThumbnail: (String) -> ImageBitmap?,
    onSelect: (String?) -> Unit,
    onDim: (Float) -> Unit,
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
                    "Шпалери лаунчера",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (items.isEmpty()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Папка порожня",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Покладіть зображення (.jpg, .png, .webp) у папку проєкту:\n\n" +
                            "app\\src\\main\\assets\\wallpapers\\\n\n" +
                            "далі запустіть PUSH.bat — після нової збірки вони з'являться тут.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {

            Column(Modifier.padding(horizontal = 16.dp)) {
                Row {
                    Text(
                        "Затемнення",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${Math.round(dim * 100)} %",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Slider(value = dim, onValueChange = onDim, valueRange = 0f..0.85f)
                Text(
                    "Чим темніше, тим краще читаються назви додатків",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.fileName }) { item ->
                    WallpaperCard(
                        item = item,
                        selected = item.fileName == selected,
                        loadThumbnail = loadThumbnail,
                        onClick = { onSelect(item.fileName) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun WallpaperCard(
    item: WallpaperItem,
    selected: Boolean,
    loadThumbnail: (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    var bitmap by remember(item.fileName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.fileName) { bitmap = loadThumbnail(item.fileName) }

    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
