package com.bell.launcher.theme

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Звідки береться вміст теми: з assets всередині APK (вбудовані теми)
 * або з розпакованої папки у filesDir/themes (імпортовані користувачем).
 */
sealed class ThemeSource {

    abstract fun open(relativePath: String): InputStream?
    abstract fun exists(relativePath: String): Boolean
    abstract val key: String

    class Assets(private val context: Context, private val dir: String) : ThemeSource() {
        override val key: String = "assets:$dir"

        override fun open(relativePath: String): InputStream? = runCatching {
            context.assets.open("$dir/${relativePath.trimStart('/')}")
        }.getOrNull()

        override fun exists(relativePath: String): Boolean = open(relativePath)?.use { true } ?: false
    }

    class Folder(val dir: File) : ThemeSource() {
        override val key: String = "file:${dir.absolutePath}"

        override fun open(relativePath: String): InputStream? {
            val f = File(dir, relativePath.trimStart('/'))
            return if (f.isFile) f.inputStream() else null
        }

        override fun exists(relativePath: String): Boolean = File(dir, relativePath.trimStart('/')).isFile

        fun fileOf(relativePath: String): File? =
            File(dir, relativePath.trimStart('/')).takeIf { it.isFile }
    }
}
