package com.headachediary.app.weather

import android.content.Context
import android.util.Log
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Чем закончилась попытка получить погоду: по этому показываем понятное сообщение об ошибке. */
enum class WeatherOutcome { OK, DISABLED, NO_LOCATION, NO_DATA }

/** Связывает местоположение, запрос погоды и запись в базе. Все функции безопасны при отсутствии сети и прав. */
object WeatherService {
    private const val TAG = "HeadacheWeather"

    /** Запасной источник знает только «сейчас», поэтому годится лишь для свежих записей. */
    private const val FALLBACK_WINDOW_MS = 90L * 60 * 1000

    private class Fetched(val outcome: WeatherOutcome, val snapshot: WeatherSnapshot? = null)

    private suspend fun fetch(context: Context, timeMs: Long): Fetched = withContext(Dispatchers.IO) {
        val coords = LocationHelper.best(context)
        if (coords == null) {
            Log.w(TAG, "No location available (permission missing, location off, or no fix yet)")
            return@withContext Fetched(WeatherOutcome.NO_LOCATION)
        }

        val isRecent = abs(System.currentTimeMillis() - timeMs) <= FALLBACK_WINDOW_MS
        val snapshot = coroutineScope {
            // Оба запроса идут одновременно, чтобы недоступность основного источника не замедляла ответ.
            val primary = async {
                runCatching {
                    WeatherClient.snapshotAt(WeatherClient.fetchAround(coords.lat, coords.lon, timeMs), timeMs)
                }.onFailure { Log.w(TAG, "Open-Meteo request failed", it) }.getOrNull()
            }
            val fallback = if (isRecent) {
                async {
                    runCatching { WeatherClient.fetchCurrentFallback(coords.lat, coords.lon) }
                        .onFailure { Log.w(TAG, "MET Norway request failed", it) }.getOrNull()
                }
            } else {
                null
            }
            primary.await() ?: fallback?.await()
        }
        if (snapshot == null) Fetched(WeatherOutcome.NO_DATA) else Fetched(WeatherOutcome.OK, snapshot)
    }

    /**
     * Записывает погоду в приступ [entryId]. Если функция выключена или данные не получены,
     * запись остаётся как была, а результат говорит почему.
     */
    suspend fun attach(context: Context, entryId: Long, force: Boolean = false): WeatherOutcome {
        if (!AppSettings.weatherEnabled(context)) return WeatherOutcome.DISABLED
        val dao = AppDatabase.get(context).dao()
        val entry = dao.byId(entryId) ?: return WeatherOutcome.NO_DATA
        // Полные данные (с изменением давления) уже есть — повторно не запрашиваем.
        if (!force && entry.pressureChange3h != null) return WeatherOutcome.OK

        val fetched = fetch(context, entry.startTime)
        fetched.snapshot?.let { s ->
            // Пустые значения не затирают уже сохранённые (запасной источник не знает изменения давления).
            dao.updateWeather(
                id = entryId,
                temperature = s.temperature ?: entry.temperature,
                pressure = s.pressure ?: entry.pressure,
                pressureChange3h = s.pressureChange3h ?: entry.pressureChange3h,
                pressureChange24h = s.pressureChange24h ?: entry.pressureChange24h,
                humidity = s.humidity ?: entry.humidity,
                weatherCode = s.weatherCode ?: entry.weatherCode,
            )
        }
        return fetched.outcome
    }

    private var cachedOutlook: Triple<Long, Double, PressureOutlook?>? = null

    /**
     * Ожидаемый перепад давления в ближайшие 24 часа. Результат кэшируется на полчаса: экран журнала
     * открывается часто, а прогноз меняется медленно. Null — прогноз получить не удалось.
     */
    suspend fun pressureOutlook(context: Context): PressureOutlook? = withContext(Dispatchers.IO) {
        val coords = LocationHelper.best(context) ?: return@withContext null
        val now = System.currentTimeMillis()
        cachedOutlook?.let { (savedAt, lat, outlook) ->
            if (now - savedAt < OUTLOOK_CACHE_MS && lat == coords.lat) return@withContext outlook
        }
        val result = coroutineScope {
            val primary = async {
                runCatching { WeatherClient.outlook(WeatherClient.fetchForecast(coords.lat, coords.lon), now) }
                    .onFailure { Log.w(TAG, "Open-Meteo forecast failed", it) }.getOrNull()
            }
            val fallback = async {
                runCatching { WeatherClient.outlook(WeatherClient.fetchForecastFallback(coords.lat, coords.lon), now) }
                    .onFailure { Log.w(TAG, "MET Norway forecast failed", it) }.getOrNull()
            }
            primary.await() ?: fallback.await()
        }
        if (result != null) cachedOutlook = Triple(now, coords.lat, result)
        result
    }

    private const val OUTLOOK_CACHE_MS = 30L * 60 * 1000

    /** Доля «обычных» часов с заметным перепадом давления за последние [days] дней; null — нет данных. */
    suspend fun baselineShare(context: Context, days: Int): Double? = withContext(Dispatchers.IO) {
        val coords = LocationHelper.best(context) ?: return@withContext null
        runCatching {
            WeatherClient.sharpChangeShare(
                WeatherClient.fetchRecent(coords.lat, coords.lon, days),
                System.currentTimeMillis(),
            )
        }.onFailure { Log.w(TAG, "Baseline request failed", it) }.getOrNull()
    }
}
