package com.bell.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Дії контекстного меню рядка домашнього списку. */
enum class HomeRowAction(val title: String) {
    MOVE_UP("Вгору"),
    MOVE_DOWN("Вниз"),
    RENAME("Перейменувати"),
    MAKE_FOLDER("Створити папку"),
    REMOVE("Прибрати з екрана"),
    APP_INFO("Про додаток"),
}

/** Які папки зараз розгорнуті (не зберігається між запусками). */
@Composable
fun rememberExpandedFolders(): SnapshotStateList<String> = remember { mutableStateListOf() }
