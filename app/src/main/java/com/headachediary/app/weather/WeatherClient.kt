package com.headachediary.app.weather

import com.headachediary.app.data.SHARP_PRESSURE_CHANGE_HPA
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Погода на момент времени: давление в гПа, температура в °C. */
data class WeatherSnapshot(
    val temperature: Double?,
    val pressure: Double?,
    val pressureChange3h: Double?,
    val pressureChange24h: Double?,
    val humidity: Int?,
    val weatherCode: Int?,
)

/** Почасовые ряды от Open-Meteo. Время — секунды Unix (UTC); пропуски — NaN. */
class Hourly(
    val times: LongArray,
    val temperature: DoubleArray,
    val humidity: DoubleArray,
    val pressure: DoubleArray,
    val weatherCode: DoubleArray,
)

/**
 * Клиент бесплатного API Open-Meteo (без ключа). Отправляются только координаты,
 * округлённые до сотых долей градуса (около километра), — записи и любые личные данные не передаются.
 */
object WeatherClient {
    private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
    private const val ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"
    private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val METNO_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"

    /** MET Norway и Nominatim требуют, чтобы приложение представлялось и давало ссылку для связи. */
    private const val USER_AGENT = "HeadacheDiary/1.0 github.com/lolvk61/HeadacheDiary"
    private const val HOURLY_FIELDS = "temperature_2m,relative_humidity_2m,surface_pressure,weather_code"
    private const val COMMON = "&hourly=$HOURLY_FIELDS&timezone=GMT&timeformat=unixtime"
    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24 * HOUR_MS

    /** Максимум истории, который отдаёт прогнозный эндпоинт. */
    const val MAX_PAST_DAYS = 92

    /** Ряды вокруг момента [timeMs]: за сутки до него и сам момент. */
    fun fetchAround(lat: Double, lon: Double, timeMs: Long): Hourly {
        val now = System.currentTimeMillis()
        val url = if (timeMs >= now - 6 * DAY_MS) {
            // Свежие данные лежат в прогнозном эндпоинте (архив отстаёт на несколько дней).
            "$FORECAST_URL?${coords(lat, lon)}&past_days=7&forecast_days=1$COMMON"
        } else {
            val start = Instant.ofEpochMilli(timeMs - DAY_MS - 2 * HOUR_MS).atZone(ZoneOffset.UTC).toLocalDate()
            val end = Instant.ofEpochMilli(timeMs).atZone(ZoneOffset.UTC).toLocalDate()
            "$ARCHIVE_URL?${coords(lat, lon)}&start_date=$start&end_date=$end$COMMON"
        }
        return parse(httpGet(url))
    }

    /** Ряды за последние [days] дней (не более [MAX_PAST_DAYS]) — для сравнения с обычной погодой. */
    fun fetchRecent(lat: Double, lon: Double, days: Int): Hourly {
        val past = days.coerceIn(1, MAX_PAST_DAYS)
        return parse(httpGet("$FORECAST_URL?${coords(lat, lon)}&past_days=$past&forecast_days=1$COMMON"))
    }

    /** Погода в час, которому принадлежит [timeMs], и изменение давления за 3 и 24 часа до него. */
    fun snapshotAt(h: Hourly, timeMs: Long): WeatherSnapshot? {
        val i = indexAt(h, timeMs)
        if (i < 0) return null
        fun value(a: DoubleArray, idx: Int): Double? = a.getOrNull(idx)?.takeIf { !it.isNaN() }
        fun change(hours: Int): Double? {
            val now = value(h.pressure, i) ?: return null
            val before = value(h.pressure, i - hours) ?: return null
            return round1(now - before)
        }
        val snapshot = WeatherSnapshot(
            temperature = value(h.temperature, i)?.let(::round1),
            pressure = value(h.pressure, i)?.let(::round1),
            pressureChange3h = change(3),
            pressureChange24h = change(24),
            humidity = value(h.humidity, i)?.roundToInt(),
            weatherCode = value(h.weatherCode, i)?.roundToInt(),
        )
        return snapshot.takeIf { it.pressure != null || it.temperature != null }
    }

    /**
     * Доля часов, когда давление за 3 часа менялось на [SHARP_PRESSURE_CHANGE_HPA] и более, —
     * «обычный фон» для сравнения с погодой в моменты приступов. Null, если данных слишком мало.
     */
    fun sharpChangeShare(h: Hourly, untilMs: Long): Double? {
        var total = 0
        var sharp = 0
        for (i in 3 until h.times.size) {
            if (h.times[i] * 1000 > untilMs) break
            val now = h.pressure[i]
            val before = h.pressure[i - 3]
            if (now.isNaN() || before.isNaN()) continue
            total++
            if (abs(now - before) >= SHARP_PRESSURE_CHANGE_HPA) sharp++
        }
        return if (total >= 24) sharp.toDouble() / total else null
    }

    /** Индекс последнего часа, начавшегося не позже [timeMs]; -1, если такого нет. */
    private fun indexAt(h: Hourly, timeMs: Long): Int {
        var found = -1
        for (i in h.times.indices) {
            if (h.times[i] * 1000 <= timeMs) found = i else break
        }
        return found
    }

    /**
     * Запасной источник — MET Norway (бесплатно, без ключа). Отдаёт только «сейчас» и прогноз, без истории,
     * поэтому изменения давления за 3 и 24 часа не заполняются. Давление приводится с уровня моря к высоте места,
     * чтобы совпадать с давлением из Open-Meteo.
     */
    fun fetchCurrentFallback(lat: Double, lon: Double): WeatherSnapshot? {
        val root = JSONObject(httpGet(String.format(Locale.US, "$METNO_URL?lat=%.2f&lon=%.2f", lat, lon)))
        val elevation = root.getJSONObject("geometry").getJSONArray("coordinates").optDouble(2, 0.0)
        val data = root.getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data")
        val details = data.getJSONObject("instant").getJSONObject("details")

        fun number(name: String): Double? = details.optDouble(name, Double.NaN).takeIf { !it.isNaN() }
        val symbol = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
        val snapshot = WeatherSnapshot(
            temperature = number("air_temperature")?.let(::round1),
            pressure = number("air_pressure_at_sea_level")?.let { round1(seaLevelToStation(it, elevation)) },
            pressureChange3h = null,
            pressureChange24h = null,
            humidity = number("relative_humidity")?.roundToInt(),
            weatherCode = symbolToWeatherCode(symbol),
        )
        return snapshot.takeIf { it.pressure != null || it.temperature != null }
    }

    /** Барометрическая формула: давление на высоте [elevationM] по давлению, приведённому к уровню моря. */
    private fun seaLevelToStation(mslHpa: Double, elevationM: Double): Double =
        mslHpa * (1 - 0.0065 * elevationM / 288.15).pow(5.255)

    /** Код погоды MET Norway («lightrain», «partlycloudy_day»…) → близкий код WMO, как у Open-Meteo. */
    private fun symbolToWeatherCode(symbol: String?): Int? = when {
        symbol == null -> null
        "thunder" in symbol -> 95
        "snow" in symbol -> 71
        "sleet" in symbol -> 67
        "showers" in symbol -> 80
        "rain" in symbol -> 61
        "fog" in symbol -> 45
        symbol.startsWith("partlycloudy") -> 2
        symbol.startsWith("cloudy") -> 3
        symbol.startsWith("fair") -> 1
        symbol.startsWith("clearsky") -> 0
        else -> null
    }

    /** Ищет город по названию: сначала геокодер Open-Meteo, при недоступности — OpenStreetMap Nominatim. */
    fun geocode(name: String, language: String): LocationHelper.Place? {
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        runCatching { geocodeOpenMeteo(query, language) }.getOrNull()?.let { return it }
        return runCatching { geocodeNominatim(query, language) }.getOrNull()
    }

    private fun geocodeOpenMeteo(query: String, language: String): LocationHelper.Place? {
        val json = httpGet("$GEOCODING_URL?name=$query&count=1&language=$language&format=json")
        val result = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
        val label = listOf(result.optString("name"), result.optString("admin1"), result.optString("country"))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        return LocationHelper.Place(label, result.getDouble("latitude"), result.getDouble("longitude"))
    }

    private fun geocodeNominatim(query: String, language: String): LocationHelper.Place? {
        val results = JSONArray(httpGet("$NOMINATIM_URL?q=$query&format=jsonv2&limit=1&accept-language=$language"))
        val result = results.optJSONObject(0) ?: return null
        val parts = result.optString("display_name").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val label = listOf(result.optString("name").ifBlank { parts.firstOrNull().orEmpty() }, parts.lastOrNull().orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        return LocationHelper.Place(label, result.getString("lat").toDouble(), result.getString("lon").toDouble())
    }

    private fun coords(lat: Double, lon: Double) =
        String.format(Locale.US, "latitude=%.2f&longitude=%.2f", lat, lon)

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            // Короткие таймауты: если сервис недоступен (блокировка), быстро переходим к запасному источнику.
            connection.connectTimeout = 5_000
            connection.readTimeout = 6_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): Hourly {
        val hourly = JSONObject(json).getJSONObject("hourly")
        val time = hourly.getJSONArray("time")
        val n = time.length()
        fun series(name: String): DoubleArray {
            val array = hourly.getJSONArray(name)
            return DoubleArray(n) { array.optDouble(it, Double.NaN) }
        }
        return Hourly(
            times = LongArray(n) { time.getLong(it) },
            temperature = series("temperature_2m"),
            humidity = series("relative_humidity_2m"),
            pressure = series("surface_pressure"),
            weatherCode = series("weather_code"),
        )
    }
}
