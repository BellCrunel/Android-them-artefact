package com.bell.launcher.data

import android.content.Context
import android.util.Log
import com.bell.launcher.data.model.AppEntry
import com.bell.launcher.data.model.AppInfo
import com.bell.launcher.data.model.AppRef
import com.bell.launcher.data.model.FolderEntry
import com.bell.launcher.data.model.HomeEntry
import com.bell.launcher.data.model.HomeLayout
import com.bell.launcher.data.model.WidgetEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Зберігає список домашнього екрана у filesDir/layout.json. */
class LayoutRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val file = File(context.filesDir, "layout.json")

    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    private fun load(): HomeLayout = runCatching {
        if (!file.isFile) return@runCatching HomeLayout()
        json.decodeFromString<HomeLayout>(file.readText())
    }.onFailure { Log.w(TAG, "layout.json пошкоджений, скидаю", it) }.getOrDefault(HomeLayout())

    private fun persist(scope: CoroutineScope, layout: HomeLayout) {
        val normalized = layout.copy(
            entries = layout.sorted.mapIndexed { index, entry -> entry.reordered(index) },
        )
        _layout.value = normalized
        scope.launch(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(normalized)) }
                .onFailure { Log.e(TAG, "Не вдалося зберегти layout", it) }
        }
    }

    val isEmpty: Boolean get() = _layout.value.entries.isEmpty()

    fun update(scope: CoroutineScope, transform: (HomeLayout) -> HomeLayout) {
        persist(scope, transform(_layout.value))
    }

    fun add(scope: CoroutineScope, entry: HomeEntry) = update(scope) { l ->
        l.copy(entries = l.sorted + entry.reordered(l.entries.size))
    }

    fun addApp(scope: CoroutineScope, app: AppInfo) {
        val exists = _layout.value.entries
            .filterIsInstance<AppEntry>()
            .any { it.app.key == app.toRef().key }
        if (exists) return
        add(scope, AppEntry(app = app.toRef()))
    }

    /** Чи є додаток серед рядків головного екрана. */
    fun isFavorite(key: String): Boolean =
        _layout.value.entries.filterIsInstance<AppEntry>().any { it.app.key == key }

    /** Додати/прибрати додаток з головного екрана одним дотиком. */
    fun toggleApp(scope: CoroutineScope, app: AppInfo) {
        val ref = app.toRef()
        val existing = _layout.value.entries
            .filterIsInstance<AppEntry>()
            .firstOrNull { it.app.key == ref.key }
        if (existing != null) remove(scope, existing.id) else addApp(scope, app)
    }

    fun remove(scope: CoroutineScope, id: String) = update(scope) { l ->
        l.copy(entries = l.sorted.filterNot { it.id == id })
    }

    /** Переставляє рядок з позиції [from] на позицію [to]. */
    fun move(scope: CoroutineScope, from: Int, to: Int) = update(scope) { l ->
        val list = l.sorted.toMutableList()
        if (from !in list.indices) return@update l
        val target = to.coerceIn(0, list.size - 1)
        if (from == target) return@update l
        val item = list.removeAt(from)
        list.add(target, item)
        l.copy(entries = list)
    }

    fun rename(scope: CoroutineScope, id: String, label: String) = update(scope) { l ->
        l.copy(
            entries = l.sorted.map { entry ->
                when {
                    entry.id != id -> entry
                    entry is AppEntry -> entry.copy(customLabel = label.ifBlank { null })
                    entry is FolderEntry -> entry.copy(name = label.ifBlank { "Папка" })
                    else -> entry
                }
            },
        )
    }

    /** Об'єднує два рядки в папку. */
    fun mergeIntoFolder(scope: CoroutineScope, targetId: String, sourceId: String) = update(scope) { l ->
        val list = l.sorted
        val target = list.firstOrNull { it.id == targetId } ?: return@update l
        val source = list.firstOrNull { it.id == sourceId } ?: return@update l
        if (target is WidgetEntry || source is WidgetEntry) return@update l

        val refs = buildList {
            when (target) {
                is AppEntry -> add(target.app)
                is FolderEntry -> addAll(target.apps)
                else -> Unit
            }
            when (source) {
                is AppEntry -> add(source.app)
                is FolderEntry -> addAll(source.apps)
                else -> Unit
            }
        }.distinctBy { it.key }

        val folder = when (target) {
            is FolderEntry -> target.copy(apps = refs)
            else -> FolderEntry(order = target.order, name = "Папка", apps = refs)
        }
        val rest = list.filterNot { it.id == targetId || it.id == sourceId }
        val insertAt = list.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        val merged = rest.toMutableList().apply {
            add(insertAt.coerceAtMost(size), folder)
        }
        l.copy(entries = merged)
    }

    fun removeFromFolder(scope: CoroutineScope, folderId: String, ref: AppRef) = update(scope) { l ->
        val list = l.sorted
        val folder = list.filterIsInstance<FolderEntry>().firstOrNull { it.id == folderId } ?: return@update l
        val remaining = folder.apps.filterNot { it.key == ref.key }
        val index = list.indexOfFirst { it.id == folderId }.coerceAtLeast(0)
        val rest = list.filterNot { it.id == folderId }.toMutableList()

        when {
            remaining.isEmpty() -> Unit
            remaining.size == 1 -> rest.add(
                index.coerceAtMost(rest.size),
                AppEntry(order = folder.order, app = remaining.first()),
            )
            else -> rest.add(index.coerceAtMost(rest.size), folder.copy(apps = remaining))
        }
        l.copy(entries = rest)
    }

    /** Додає додаток у наявну папку. */
    fun addToFolder(scope: CoroutineScope, folderId: String, ref: AppRef) = update(scope) { l ->
        l.copy(
            entries = l.sorted.map { entry ->
                if (entry is FolderEntry && entry.id == folderId) {
                    entry.copy(apps = (entry.apps + ref).distinctBy { it.key })
                } else {
                    entry
                }
            },
        )
    }

    /** Перетворює рядок-додаток на папку з цим додатком усередині. */
    fun convertToFolder(scope: CoroutineScope, entryId: String, name: String) = update(scope) { l ->
        val list = l.sorted
        val target = list.filterIsInstance<AppEntry>().firstOrNull { it.id == entryId } ?: return@update l
        l.copy(
            entries = list.map { entry ->
                if (entry.id == entryId) {
                    FolderEntry(order = target.order, name = name, apps = listOf(target.app))
                } else {
                    entry
                }
            },
        )
    }

    fun widgetIds(): List<Int> = _layout.value.entries.filterIsInstance<WidgetEntry>().map { it.appWidgetId }

    /** Стартовий список із кількох найпоширеніших додатків. */
    fun seedIfEmpty(scope: CoroutineScope, apps: List<AppInfo>) {
        if (!isEmpty || apps.isEmpty()) return

        val hints = listOf("youtube", "dialer", "phone", "camera", "vending", "chrome", "messag")
        val picked = LinkedHashMap<String, AppInfo>()
        hints.forEach { hint ->
            apps.firstOrNull { it.packageName.contains(hint, true) && it.packageName !in picked }
                ?.let { picked[it.packageName] = it }
        }
        apps.take(8).forEach { app ->
            if (picked.size >= 6) return@forEach
            if (app.packageName !in picked) picked[app.packageName] = app
        }

        val entries = picked.values.mapIndexed { index, app ->
            AppEntry(order = index, app = app.toRef())
        }
        persist(scope, HomeLayout(entries))
    }

    companion object {
        private const val TAG = "LayoutRepository"
    }
}
