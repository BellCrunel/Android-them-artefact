package com.bell.launcher.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Один рядок домашнього списку. Порядок задає [order].
 */
@Serializable
sealed class HomeEntry {
    abstract val id: String
    abstract val order: Int
    abstract fun reordered(order: Int): HomeEntry
}

@Serializable
@SerialName("app")
data class AppEntry(
    override val id: String = UUID.randomUUID().toString(),
    override val order: Int = 0,
    val app: AppRef,
    val customLabel: String? = null,
) : HomeEntry() {
    override fun reordered(order: Int) = copy(order = order)
    val label: String get() = customLabel ?: app.label.ifBlank { app.packageName }
}

@Serializable
@SerialName("folder")
data class FolderEntry(
    override val id: String = UUID.randomUUID().toString(),
    override val order: Int = 0,
    val name: String = "Папка",
    val apps: List<AppRef> = emptyList(),
) : HomeEntry() {
    override fun reordered(order: Int) = copy(order = order)
}

@Serializable
@SerialName("widget")
data class WidgetEntry(
    override val id: String = UUID.randomUUID().toString(),
    override val order: Int = 0,
    val appWidgetId: Int,
    val heightDp: Int = 120,
    val providerPackage: String = "",
) : HomeEntry() {
    override fun reordered(order: Int) = copy(order = order)
}

@Serializable
data class HomeLayout(
    val entries: List<HomeEntry> = emptyList(),
) {
    val sorted: List<HomeEntry> get() = entries.sortedBy { it.order }
}
