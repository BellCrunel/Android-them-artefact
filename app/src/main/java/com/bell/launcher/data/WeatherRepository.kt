package com.bell.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Weather(
    val temperatureC: Double,
    val code: Int,
    val place: String,
    val updatedAt: Long,
) {
    val symbol: String get() = symbolFor(code)
    val shortText: String get() = "$symbol ${Math.round(temperatureC)}°"

    companion object {
        fun symbolFor(code: Int): String = when (code) {
            0 -> "☀"
            1, 2 -> "⛅"
            3 -> "☁"
            45, 48 -> "🌫"
            in 51..57 -> "🌦"
            in 61..67 -> "🌧"
            in 71..77 -> "❄"
            in 80..82 -> "🌦"
            85, 86 -> "🌨"
            in 95..99 -> "⛈"
            else -> "☁"
        }
    }
}

/**
 * Погода через Open-Meteo — безкоштовно й без ключа API.
 * Координати беруться або з останньої відомої геолокації (дозвіл COARSE),
 * або з міста, вписаного вручну в налаштуваннях.
 */
class WeatherRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("launcher_weather", Context.MODE_PRIVATE)

    private val _weather = MutableStateFlow(readCache())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private fun readCache(): Weather? {
        if (!prefs.contains(KEY_TEMP)) return null
        return Weather(
            temperatureC = prefs.getFloat(KEY_TEMP, 0f).toDouble(),
            code = prefs.getInt(KEY_CODE, 3),
            place = prefs.getString(KEY_PLACE, "").orEmpty(),
            updatedAt = prefs.getLong(KEY_TIME, 0L),
        )
    }

    private fun writeCache(weather: Weather) {
        prefs.edit()
            .putFloat(KEY_TEMP, weather.temperatureC.toFloat())
            .putInt(KEY_CODE, weather.code)
            .putString(KEY_PLACE, weather.place)
            .putLong(KEY_TIME, weather.updatedAt)
            .apply()
        _weather.value = weather
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Оновлює погоду, якщо кеш старший за 30 хвилин (або [force]). */
    suspend fun refresh(settings: LauncherSettings, force: Boolean = false) {
        if (!settings.weatherEnabled) return
        val cached = _weather.value
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < CACHE_MS) return

        val point = resolveLocation(settings) ?: return
        val fetched = fetch(point.first, point.second, point.third) ?: return
        writeCache(fetched)
    }

    private suspend fun resolveLocation(settings: LauncherSettings): Triple<Double, Double, String>? {
        if (settings.useLocation && hasLocationPermission()) {
            lastKnownLocation()?.let { return Triple(it.first, it.second, "") }
        }
        if (settings.manualLat != null && settings.manualLon != null) {
            return Triple(settings.manualLat, settings.manualLon, settings.manualCity)
        }
        if (settings.manualCity.isNotBlank()) {
            return geocode(settings.manualCity)
        }
        return null
    }

    private fun lastKnownLocation(): Pair<Double, Double>? = runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        providers.firstNotNullOfOrNull { provider ->
            @Suppress("MissingPermission")
            manager.getLastKnownLocation(provider)?.let { it.latitude to it.longitude }
        }
    }.getOrNull()

    /** Знаходить координати міста за назвою. */
    suspend fun geocode(city: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(city.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&format=json")
            ?: return@withContext null
        runCatching {
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
            val first = results?.firstOrNull()?.jsonObject ?: return@runCatching null
            Triple(
                first["latitude"]!!.jsonPrimitive.content.toDouble(),
                first["longitude"]!!.jsonPrimitive.content.toDouble(),
                first["name"]?.jsonPrimitive?.content ?: city,
            )
        }.onFailure { Log.w(TAG, "geocode failed", it) }.getOrNull()
    }

    private suspend fun fetch(lat: Double, lon: Double, place: String): Weather? = withContext(Dispatchers.IO) {
        val body = get(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"
        ) ?: return@withContext null

        runCatching {
            val current = json.parseToJsonElement(body).jsonObject["current"]!!.jsonObject
            Weather(
                temperatureC = current["temperature_2m"]!!.jsonPrimitive.content.toDouble(),
                code = current["weather_code"]?.jsonPrimitive?.content?.toInt() ?: 3,
                place = place,
                updatedAt = System.currentTimeMillis(),
            )
        }.onFailure { Log.w(TAG, "weather parse failed", it) }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "BellLauncher/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "HTTP failed: $url", it) }.getOrNull()

    companion object {
        private const val TAG = "WeatherRepository"
        private const val CACHE_MS = 30 * 60 * 1000L
        private const val KEY_TEMP = "temp"
        private const val KEY_CODE = "code"
        private const val KEY_PLACE = "place"
        private const val KEY_TIME = "time"
    }
}
