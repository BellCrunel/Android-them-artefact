package com.bell.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

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

/** Що саме сталося під час останнього оновлення — щоб показати це в налаштуваннях. */
enum class WeatherStatus {
    IDLE,
    LOADING,
    OK,
    DISABLED,
    NO_PLACE,
    NO_NETWORK,
}

/**
 * Погода через Open-Meteo — безкоштовно й без ключа API.
 *
 * Місце визначається за таким ланцюжком (перше, що спрацювало):
 *  1. геолокація, якщо ввімкнена й дозвіл наданий — спочатку остання відома,
 *     потім активний однократний запит координат;
 *  2. координати міста, вписаного вручну;
 *  3. геокодинг назви міста, якщо координати ще не знайдені;
 *  4. **часовий пояс пристрою** — «Europe/Kyiv» → «Kyiv».
 *
 * Пункт 4 — головне виправлення: раніше без дозволу на геолокацію й без
 * вписаного міста метод просто мовчки повертав null, тож погода не з'являлася
 * ніколи і без жодного повідомлення.
 */
class WeatherRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("launcher_weather", Context.MODE_PRIVATE)

    private val _weather = MutableStateFlow(readCache())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _status = MutableStateFlow(if (_weather.value != null) WeatherStatus.OK else WeatherStatus.IDLE)
    val status: StateFlow<WeatherStatus> = _status.asStateFlow()

    /** Захист від двох паралельних оновлень (onResume + фоновий цикл). */
    private val running = AtomicBoolean(false)

    /** Людський опис стану для екрана налаштувань. */
    fun statusText(): String {
        val current = _weather.value
        return when (_status.value) {
            WeatherStatus.LOADING -> "Оновлюю…"
            WeatherStatus.DISABLED -> "Вимкнено"
            WeatherStatus.NO_PLACE -> "Не вдалося визначити місце. Впишіть місто вручну"
            WeatherStatus.NO_NETWORK -> "Немає зв'язку з сервером погоди"
            WeatherStatus.OK -> current?.let { "${it.shortText}  ${it.place}".trim() } ?: "Немає даних"
            WeatherStatus.IDLE -> current?.let { "${it.shortText}  ${it.place}".trim() } ?: "Ще не оновлювалась"
        }
    }

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
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Оновлює погоду, якщо кеш старший за 30 хвилин (або [force]). */
    suspend fun refresh(settings: LauncherSettings, force: Boolean = false) {
        if (!settings.weatherEnabled) {
            _status.value = WeatherStatus.DISABLED
            return
        }
        val cached = _weather.value
        if (!force && cached != null && System.currentTimeMillis() - cached.updatedAt < CACHE_MS) {
            _status.value = WeatherStatus.OK
            return
        }
        if (!running.compareAndSet(false, true)) return

        // try/finally обов'язковий: якщо запит скасують (наприклад, екран
        // закрили посеред очікування координат), прапорець мусить зніматися,
        // інакше жодне наступне оновлення вже не почнеться.
        try {
            _status.value = WeatherStatus.LOADING
            val point = resolveLocation(settings)
            if (point == null) {
                Log.w(TAG, "не вдалося визначити місце для погоди")
                _status.value = WeatherStatus.NO_PLACE
                return
            }
            val fetched = fetch(point.first, point.second, point.third)
            if (fetched == null) {
                _status.value = WeatherStatus.NO_NETWORK
                return
            }
            writeCache(fetched)
            _status.value = WeatherStatus.OK
        } finally {
            running.set(false)
            if (_status.value == WeatherStatus.LOADING) {
                _status.value = if (_weather.value != null) WeatherStatus.OK else WeatherStatus.IDLE
            }
        }
    }

    private suspend fun resolveLocation(settings: LauncherSettings): Triple<Double, Double, String>? {
        if (settings.useLocation && hasLocationPermission()) {
            currentLocation()?.let { (lat, lon) ->
                return Triple(lat, lon, reverseGeocode(lat, lon))
            }
        }
        if (settings.manualLat != null && settings.manualLon != null) {
            return Triple(settings.manualLat, settings.manualLon, settings.manualCity)
        }
        if (settings.manualCity.isNotBlank()) {
            geocode(settings.manualCity)?.let { return it }
        }
        // Останній рубіж: місто з часового поясу пристрою. Працює завжди,
        // навіть без жодного дозволу — точність до міста цілком достатня.
        return timeZoneCity()?.let { geocode(it) }
    }

    /** Остання відома позиція, а якщо її немає — активний однократний запит. */
    private suspend fun currentLocation(): Pair<Double, Double>? {
        lastKnownLocation()?.let { return it }
        return requestSingleFix()
    }

    private fun lastKnownLocation(): Pair<Double, Double>? = runCatching {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        providers.firstNotNullOfOrNull { provider ->
            @Suppress("MissingPermission")
            manager.getLastKnownLocation(provider)?.let { it.latitude to it.longitude }
        }
    }.getOrNull()

    /**
     * Просимо систему один раз віддати координати. На свіжому телефоні
     * (або після перезавантаження) кешу «останньої позиції» просто немає,
     * і без цього запиту погода не з'явиться взагалі.
     */
    private suspend fun requestSingleFix(): Pair<Double, Double>? = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
        suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (manager == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val delivered = AtomicBoolean(false)
            fun deliver(location: Location?) {
                if (delivered.compareAndSet(false, true) && cont.isActive) {
                    cont.resume(location?.let { it.latitude to it.longitude })
                }
            }

            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { runCatching { signal.cancel() } }
                    @Suppress("MissingPermission")
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        context.mainExecutor,
                    ) { location -> deliver(location) }
                } else {
                    // На старих API беремо потік оновлень і знімаємося з нього
                    // після першої ж координати — requestSingleUpdate застарів.
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { manager.removeUpdates(this) }
                            deliver(location)
                        }

                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit

                        @Deprecated("Потрібен лише для API < 29")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    }
                    cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                    @Suppress("MissingPermission")
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, context.mainLooper)
                }
            }.isSuccess
            if (!ok) deliver(null)
        }
    }

    /** Назва населеного пункту за координатами — щоб у рядку було не лише число. */
    private suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }.orEmpty()
        }.getOrDefault("")
    }

    /** «Europe/Kyiv» → «Kyiv». Останній фолбек, коли інших даних немає. */
    private fun timeZoneCity(): String? {
        val id = runCatching { TimeZone.getDefault().id }.getOrNull() ?: return null
        val city = id.substringAfterLast('/').replace('_', ' ').trim()
        return city.takeIf { it.length > 2 }
    }

    /** Знаходить координати міста за назвою. */
    suspend fun geocode(city: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        val language = Locale.getDefault().language.ifBlank { "en" }
        val encoded = URLEncoder.encode(city.trim(), "UTF-8")
        val body = get(
            "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=$encoded&count=1&language=$language&format=json"
        ) ?: return@withContext null
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
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ArtefactLauncher/1.0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code for $url")
                return@runCatching null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "HTTP failed: $url", it) }.getOrNull()

    companion object {
        private const val TAG = "WeatherRepository"
        private const val CACHE_MS = 30 * 60 * 1000L
        private const val LOCATION_TIMEOUT_MS = 9_000L
        private const val KEY_TEMP = "temp"
        private const val KEY_CODE = "code"
        private const val KEY_PLACE = "place"
        private const val KEY_TIME = "time"
    }
}
