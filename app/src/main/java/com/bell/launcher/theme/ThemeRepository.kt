package com.bell.launcher.theme

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bell.launcher.theme.model.ThemeManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class ThemeRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val themesDir: File = File(context.filesDir, "themes").apply { mkdirs() }

    private val _themes = MutableStateFlow<List<LauncherThemeData>>(emptyList())
    val themes: StateFlow<List<LauncherThemeData>> = _themes.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val result = mutableListOf<LauncherThemeData>()
        result += loadAssetThemes()
        result += loadInstalledThemes()
        _themes.value = result.distinctBy { it.id }
    }

    fun byId(id: String?): LauncherThemeData? {
        if (id == null) return null
        return _themes.value.firstOrNull { it.id == id }
    }

    fun defaultTheme(): LauncherThemeData =
        byId(BUILTIN_DEFAULT) ?: _themes.value.firstOrNull() ?: fallbackTheme()

    private fun fallbackTheme(): LauncherThemeData = LauncherThemeData(
        manifest = ThemeManifest(id = "fallback", name = "Default"),
        source = ThemeSource.Folder(File(themesDir, "__none__")),
    )

    // ----------------------------------------------------------------- assets

    private fun loadAssetThemes(): List<LauncherThemeData> {
        val dirs = runCatching { context.assets.list(ASSET_ROOT)?.toList().orEmpty() }.getOrDefault(emptyList())
        return dirs.mapNotNull { dir ->
            val source = ThemeSource.Assets(context, "$ASSET_ROOT/$dir")
            parseManifest(source)?.let { LauncherThemeData(it, source, removable = false) }
        }
    }

    // -------------------------------------------------------------- installed

    private fun loadInstalledThemes(): List<LauncherThemeData> {
        val dirs = themesDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        return dirs.mapNotNull { dir ->
            val source = ThemeSource.Folder(dir)
            parseManifest(source)?.let { LauncherThemeData(it, source, removable = true) }
        }
    }

    private fun parseManifest(source: ThemeSource): ThemeManifest? {
        val stream: InputStream = source.open(MANIFEST) ?: return null
        return runCatching {
            stream.use { json.decodeFromString<ThemeManifest>(it.readBytes().decodeToString()) }
        }.onFailure { Log.w(TAG, "Bad manifest in ${source.key}", it) }.getOrNull()
    }

    // ----------------------------------------------------------------- import

    /** Розпаковує .ltheme/.zip у filesDir/themes/<id> і повертає встановлену тему. */
    suspend fun install(uri: Uri): Result<LauncherThemeData> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "theme-import-${System.currentTimeMillis()}")
            staging.mkdirs()
            try {
                context.contentResolver.openInputStream(uri)?.use { unzip(it, staging) }
                    ?: error("Не вдалося відкрити файл")

                val root = findManifestRoot(staging) ?: error("У архіві немає manifest.json")
                val source = ThemeSource.Folder(root)
                val manifest = parseManifest(source) ?: error("manifest.json пошкоджений")

                val target = File(themesDir, sanitize(manifest.id))
                if (target.exists()) target.deleteRecursively()
                target.parentFile?.mkdirs()
                if (!root.renameTo(target)) {
                    root.copyRecursively(target, overwrite = true)
                }
                val installed = LauncherThemeData(manifest, ThemeSource.Folder(target), removable = true)
                reload()
                installed
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    fun delete(id: String): Boolean {
        val dir = File(themesDir, sanitize(id))
        val ok = dir.exists() && dir.deleteRecursively()
        if (ok) reload()
        return ok
    }

    private fun findManifestRoot(dir: File): File? {
        if (File(dir, MANIFEST).isFile) return dir
        dir.listFiles()?.filter { it.isDirectory }?.forEach { child ->
            if (File(child, MANIFEST).isFile) return child
        }
        return null
    }

    private fun unzip(input: InputStream, target: File) {
        val canonicalTarget = target.canonicalPath
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(target, entry.name)
                // Захист від zip-slip
                if (!out.canonicalPath.startsWith(canonicalTarget)) {
                    throw SecurityException("Небезпечний шлях у архіві: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        private const val TAG = "ThemeRepository"
        private const val ASSET_ROOT = "themes"
        private const val MANIFEST = "manifest.json"
        const val BUILTIN_DEFAULT = "builtin.aurora"
    }
}
