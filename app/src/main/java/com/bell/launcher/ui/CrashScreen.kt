package com.bell.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Показується замість лаунчера, якщо попередній запуск завершився падінням.
 * Навмисно не залежить від теми, іконок і репозиторіїв — щоб не впасти так само.
 */
@Composable
fun CrashScreen(
    text: String,
    externalPath: String,
    onCopy: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF12121A))
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Text(
            "Лаунчер впав",
            color = Color(0xFFFF8A80),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Надішліть цей текст (або скріншот) — і я виправлю.",
            color = Color(0xFFBDBDD0),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy) { Text("Копіювати") }
            OutlinedButton(onClick = onContinue) { Text("Спробувати запустити") }
        }

        Spacer(Modifier.height(12.dp))

        SelectionContainer(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF08080C))
                .padding(10.dp),
        ) {
            Text(
                text = text,
                color = Color(0xFFE0E0EA),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Файл із помилкою:\n$externalPath",
            color = Color(0xFF8A8AA0),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
