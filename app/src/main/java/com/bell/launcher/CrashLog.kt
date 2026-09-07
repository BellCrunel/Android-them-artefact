package com.bell.launcher

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Діагностика запуску.
 *
 * 1. Перехоплює необроблені падіння і зберігає стек у файл.
 * 2. Веде журнал етапів запуску (breadcrumbs). Якщо попередній запуск не дійшов
 *    до позначки READY — наступного разу показуємо звіт замість чорного екрана.
 *
 * Це потрібно тому, що HOME-додаток при падінні система просто перезапускає по колу,
 * і користувач бачить лише чорний прямокутник без жодного повідомлення.
 */
object CrashLog {

    private const val CRASH_FILE = "last_crash.txt"
    private const val TRACE_FILE = "startup_trace.txt"
    private const val READY = "READY"

    private val stamp: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    /** Журнал попереднього запуску, прочитаний на старті процесу. */
    @Volatile
    var previousRun: String? = null
        private set

    /** Чи дійшов попередній запуск до робочого стану. */
    @Volatile
    var previousRunCompleted: Boolean = true
        private set

    // ------------------------------------------------------------ падіння

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Записати помилку вручну (напр. збій ініціалізації до старту UI). */
    fun record(context: Context, throwable: Throwable) {
        runCatching { writeCrash(context, Thread.currentThread(), throwable) }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stack = StringWriter().also { sw ->
            PrintWriter(sw).use { throwable.printStackTrace(it) }
        }.toString()

        val text = buildString {
            appendLine(header(context))
            appendLine("Потік: ${thread.name}")
            appendLine()
            appendLine("---- ВИНЯТОК ----")
            appendLine(stack)
            appendLine("---- ЕТАПИ ЗАПУСКУ ----")
            appendLine(readTrace(context) ?: "(немає)")
        }

        writeBoth(context, CRASH_FILE, text)
    }

    private fun header(context: Context) = buildString {
        appendLine("Artefact Launcher — звіт про запуск")
        appendLine("Час: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Пристрій: ${Build.MANUFACTURER} ${Build.MODEL}")
    }

    // -------------------------------------------------------- етапи запуску

    /** Викликати найпершим у Application.onCreate. */
    fun beginRun(context: Context) {
        val prior = readTrace(context)
        previousRun = prior
        previousRunCompleted = prior == null || prior.contains(READY)
        runCatching {
            File(context.filesDir, TRACE_FILE).writeText(header(context) + "\n[$stamp] старт процесу\n")
        }
    }

    fun step(context: Context, name: String) {
        runCatching {
            File(context.filesDir, TRACE_FILE).appendText("[$stamp] $name\n")
        }
    }

    /** Викликати після першої успішної композиції. */
    fun markReady(context: Context) {
        step(context, READY)
        runCatching { readTrace(context)?.let { writeBoth(context, TRACE_FILE, it) } }
    }

    private fun readTrace(context: Context): String? = runCatching {
        val file = File(context.filesDir, TRACE_FILE)
        if (file.isFile && file.length() > 0) file.readText() else null
    }.getOrNull()

    // ---------------------------------------------------------------- звіт

    /** Текст для екрана діагностики: падіння, якщо було, інакше журнал етапів. */
    fun report(context: Context): String? {
        readCrash(context)?.let { return it }
        if (!previousRunCompleted) {
            return buildString {
                appendLine(header(context))
                appendLine()
                appendLine("Винятку не було — лаунчер завис або був убитий системою")
                appendLine("до того, як встиг намалювати екран.")
                appendLine()
                appendLine("---- ЕТАПИ ПОПЕРЕДНЬОГО ЗАПУСКУ ----")
                appendLine(previousRun ?: "(журнал порожній)")
                appendLine()
                appendLine("Останній рядок вище показує, на чому все зупинилося.")
            }
        }
        return null
    }

    private fun readCrash(context: Context): String? = runCatching {
        val file = File(context.filesDir, CRASH_FILE)
        if (file.isFile && file.length() > 0) file.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, CRASH_FILE).delete() }
        runCatching { context.getExternalFilesDir(null)?.let { File(it, CRASH_FILE).delete() } }
        previousRunCompleted = true
    }

    fun externalPath(context: Context): String =
        runCatching { File(context.getExternalFilesDir(null), CRASH_FILE).absolutePath }
            .getOrDefault("(недоступно)")

    private fun writeBoth(context: Context, name: String, text: String) {
        runCatching { File(context.filesDir, name).writeText(text) }
        // Дубль у зовнішню папку — щоб файл можна було забрати з ПК
        runCatching {
            context.getExternalFilesDir(null)?.let { File(it, name).writeText(text) }
        }
    }
}
