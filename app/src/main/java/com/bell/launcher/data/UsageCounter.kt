package com.bell.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Скільки і як давно користувач відкривав кожен додаток **із цього лаунчера**.
 *
 * Свідомо не використовуємо `UsageStatsManager`: він дав би історію за минулі
 * тижні, але потребує особливого дозволу `PACKAGE_USAGE_STATS`, який навіть
 * не питається діалогом (користувача треба вести в системні налаштування
 * руками), а на частині прошивок мовчки віддає порожній список.
 *
 * Вага запуску згасає з часом. Без цього додаток, яким користувалися щодня
 * пів року тому, назавжди залишився б угорі списку. Зберігаємо не список
 * подій, а сам бал: при кожному запуску спершу згашуємо старе значення до
 * «зараз», потім додаємо одиницю. Один `Float` на додаток.
 */
class UsageCounter(context: Context) {

    private val prefs = context.getSharedPreferences("launcher_usage", Context.MODE_PRIVATE)

    private val _scores = MutableStateFlow(readAll())
    /** Ключ додатка (`package/activity`) → бал із урахуванням давності. */
    val scores: StateFlow<Map<String, Float>> = _scores.asStateFlow()

    private fun readAll(): Map<String, Float> {
        val now = System.currentTimeMillis()
        return prefs.all.keys
            .filter { it.endsWith(SUFFIX_SCORE) }
            .associate { key ->
                val appKey = key.removeSuffix(SUFFIX_SCORE)
                appKey to decayed(appKey, now)
            }
            .filterValues { it > MIN_SCORE }
    }

    /** Бал додатка, згашений до моменту [now]. */
    private fun decayed(appKey: String, now: Long): Float {
        val score = prefs.getFloat(appKey + SUFFIX_SCORE, 0f)
        if (score <= 0f) return 0f
        val at = prefs.getLong(appKey + SUFFIX_AT, now)
        val elapsed = (now - at).coerceAtLeast(0L)
        val factor = 0.5.pow(elapsed.toDouble() / HALF_LIFE_MS).toFloat()
        return score * factor
    }

    /** Викликається на кожен запуск додатка з лаунчера. */
    fun record(packageName: String, activityName: String) {
        val appKey = "$packageName/$activityName"
        val now = System.currentTimeMillis()
        val next = decayed(appKey, now) + 1f
        prefs.edit()
            .putFloat(appKey + SUFFIX_SCORE, next)
            .putLong(appKey + SUFFIX_AT, now)
            .apply()
        _scores.value = _scores.value + (appKey to next)
    }

    /** Забути статистику — на випадок, якщо порядок «залип» і хочеться почати з нуля. */
    fun clear() {
        prefs.edit().clear().apply()
        _scores.value = emptyMap()
    }

    private companion object {
        /** За два тижні без запусків вага падає вдвічі. */
        const val HALF_LIFE_MS = 14L * 24 * 60 * 60 * 1000
        const val MIN_SCORE = 0.01f
        const val SUFFIX_SCORE = "#s"
        const val SUFFIX_AT = "#t"
    }
}
