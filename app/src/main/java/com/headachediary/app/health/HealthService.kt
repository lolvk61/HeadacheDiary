package com.headachediary.app.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.SHORT_SLEEP_MINUTES
import com.headachediary.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/** Данные с часов на момент начала приступа. Пустые поля — данных нет или доступ не выдан. */
data class HealthSnapshot(val sleepMinutes: Int?, val steps24h: Int?, val restingHeartRate: Int?) {
    val isEmpty: Boolean get() = sleepMinutes == null && steps24h == null && restingHeartRate == null
}

/** «Обычный фон» за период — для сравнения с моментами приступов. Null внутри — данных мало. */
data class HealthBaseline(val shortSleepShare: Double?, val avgSteps: Int?, val avgRestingHeartRate: Int?)

/**
 * Чтение из Health Connect того, что туда пишут часы и их приложения (например, Nothing X):
 * сон, шаги и пульс в покое. Приложение только читает и ничего не записывает.
 */
object HealthService {
    private const val TAG = "HeadacheHealth"
    private const val SLEEP_LOOKBACK_HOURS = 30L
    private const val MIN_NIGHT_MINUTES = 120L

    /** Права на чтение, которые запрашиваем у пользователя. */
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val heartPermission = HealthPermission.getReadPermission(RestingHeartRateRecord::class)

    fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean = sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private suspend fun grantedPermissions(client: HealthConnectClient): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** Есть ли хотя бы одно выданное право на чтение. */
    suspend fun hasAnyAccess(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext false
        grantedPermissions(HealthConnectClient.getOrCreate(context)).any { it in permissions }
    }

    // --- сон ---

    private suspend fun readSleep(client: HealthConnectClient, from: Instant, to: Instant): List<SleepSessionRecord> {
        val result = mutableListOf<SleepSessionRecord>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = token,
                ),
            )
            result += response.records
            token = response.pageToken
        } while (token != null)
        return result
    }

    /** Длительность сна в минутах без времени, проведённого без сна (если часы записали стадии). */
    private fun netSleepMinutes(session: SleepSessionRecord): Long {
        val awakeStages = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )
        val total = Duration.between(session.startTime, session.endTime).toMinutes()
        val awake = session.stages
            .filter { it.stage in awakeStages }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        return (total - awake).coerceAtLeast(0)
    }

    // --- снимок на момент приступа ---

    /** Сон, шаги и пульс в покое перед [startMs]; null, если функция выключена или ничего не нашлось. */
    suspend fun snapshotBefore(context: Context, startMs: Long): HealthSnapshot? = withContext(Dispatchers.IO) {
        if (!AppSettings.healthEnabled(context) || !isAvailable(context)) return@withContext null
        val client = HealthConnectClient.getOrCreate(context)
        val granted = grantedPermissions(client)
        if (granted.none { it in permissions }) return@withContext null

        val start = Instant.ofEpochMilli(startMs)

        val sleep = if (sleepPermission in granted) {
            runCatching {
                // Основной сон — самый длинный из закончившихся перед приступом за последние ~30 часов.
                readSleep(client, start.minus(Duration.ofHours(SLEEP_LOOKBACK_HOURS + 12)), start)
                    .filter { !it.endTime.isAfter(start) && it.endTime.isAfter(start.minus(Duration.ofHours(SLEEP_LOOKBACK_HOURS))) }
                    .maxOfOrNull { netSleepMinutes(it) }
                    ?.toInt()
            }.onFailure { Log.w(TAG, "Sleep read failed", it) }.getOrNull()
        } else {
            null
        }

        val steps = if (stepsPermission in granted) {
            runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.minus(Duration.ofHours(24)), start),
                    ),
                )[StepsRecord.COUNT_TOTAL]?.toInt()
            }.onFailure { Log.w(TAG, "Steps read failed", it) }.getOrNull()
        } else {
            null
        }

        val heart = if (heartPermission in granted) {
            runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start.minus(Duration.ofHours(48)), start),
                    ),
                ).records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
            }.onFailure { Log.w(TAG, "Heart rate read failed", it) }.getOrNull()
        } else {
            null
        }

        HealthSnapshot(sleep, steps, heart).takeUnless { it.isEmpty }
    }

    /**
     * Записывает данные с часов в приступ [entryId]. Не затирает уже сохранённые значения пустыми.
     * Возвращает true, если у записи теперь есть хоть какие-то данные.
     */
    suspend fun attach(context: Context, entryId: Long, force: Boolean = false): Boolean {
        if (!AppSettings.healthEnabled(context)) return false
        val dao = AppDatabase.get(context).dao()
        val entry = dao.byId(entryId) ?: return false
        if (!force && entry.sleepMinutes != null && entry.steps24h != null && entry.restingHeartRate != null) return true

        val snapshot = snapshotBefore(context, entry.startTime)
        if (snapshot != null) {
            dao.updateHealth(
                id = entryId,
                sleepMinutes = snapshot.sleepMinutes ?: entry.sleepMinutes,
                steps24h = snapshot.steps24h ?: entry.steps24h,
                restingHeartRate = snapshot.restingHeartRate ?: entry.restingHeartRate,
            )
        }
        return snapshot != null || entry.sleepMinutes != null || entry.steps24h != null || entry.restingHeartRate != null
    }

    /**
     * Дописывает данные в недавние записи, у которых их ещё нет: часы синхронизируются не сразу,
     * поэтому сон предыдущей ночи может появиться в Health Connect уже после записи приступа.
     */
    suspend fun fillRecent(context: Context, days: Int = 7) {
        if (!AppSettings.healthEnabled(context)) return
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val dao = AppDatabase.get(context).dao()
        dao.allOnce()
            .filter { it.startTime >= since && (it.sleepMinutes == null || it.steps24h == null || it.restingHeartRate == null) }
            .forEach { attach(context, it.id) }
    }

    // --- обычный фон за период ---

    /** Доля коротких ночей, средние шаги за день и пульс в покое за последние [days] дней. */
    suspend fun baseline(context: Context, days: Int): HealthBaseline? = withContext(Dispatchers.IO) {
        if (!AppSettings.healthEnabled(context) || !isAvailable(context)) return@withContext null
        val client = HealthConnectClient.getOrCreate(context)
        val granted = grantedPermissions(client)
        if (granted.none { it in permissions }) return@withContext null

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val from = now.minus(Duration.ofDays(days.toLong() + 1))

        val shortShare = if (sleepPermission in granted) {
            runCatching {
                // Для каждой даты пробуждения берём самый длинный сон; дни с одной лишь дрёмой не считаем ночами.
                val longestPerDay = readSleep(client, from, now)
                    .groupBy { it.endTime.atZone(zone).toLocalDate() }
                    .mapValues { (_, sessions) -> sessions.maxOf { netSleepMinutes(it) } }
                    .values
                    .filter { it >= MIN_NIGHT_MINUTES }
                if (longestPerDay.size >= 7) longestPerDay.count { it < SHORT_SLEEP_MINUTES }.toDouble() / longestPerDay.size else null
            }.onFailure { Log.w(TAG, "Sleep baseline failed", it) }.getOrNull()
        } else {
            null
        }

        val avgSteps = if (stepsPermission in granted) {
            runCatching {
                val today: LocalDate = LocalDate.now()
                val buckets = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(
                            LocalDateTime.of(today.minusDays(days.toLong()), java.time.LocalTime.MIDNIGHT),
                            LocalDateTime.of(today, java.time.LocalTime.MIDNIGHT),
                        ),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                )
                val perDay = buckets.mapNotNull { it.result[StepsRecord.COUNT_TOTAL] }.filter { it > 0 }
                if (perDay.size >= 7) perDay.average().toInt() else null
            }.onFailure { Log.w(TAG, "Steps baseline failed", it) }.getOrNull()
        } else {
            null
        }

        val avgHeart = if (heartPermission in granted) {
            runCatching {
                val records = client.readRecords(
                    ReadRecordsRequest(
                        recordType = RestingHeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, now),
                    ),
                ).records
                if (records.size >= 7) records.map { it.beatsPerMinute }.average().toInt() else null
            }.onFailure { Log.w(TAG, "Heart baseline failed", it) }.getOrNull()
        } else {
            null
        }

        HealthBaseline(shortShare, avgSteps, avgHeart)
    }
}
