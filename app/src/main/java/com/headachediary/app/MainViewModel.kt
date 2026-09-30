package com.headachediary.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.AutoBackup
import com.headachediary.app.data.Backup
import com.headachediary.app.data.DayFactors
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.PainFreeDay
import com.headachediary.app.health.HealthService
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.ui.toLocalDate
import com.headachediary.app.weather.LocationHelper
import com.headachediary.app.weather.WeatherOutcome
import com.headachediary.app.weather.WeatherService
import com.headachediary.app.widget.PainWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportResult(val added: Int, val skipped: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context = app.applicationContext
    private val dao = AppDatabase.get(app).dao()

    val entries: StateFlow<List<HeadacheEntry>> = dao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Дни (число дней от 1970-01-01), которые пользователь отметил как «без боли». */
    val painFreeDays: StateFlow<Set<Long>> = dao.painFreeDays()
        .map { list -> list.map { it.day }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Факторы дня по дню (число дней от 1970-01-01). */
    val dayFactors: StateFlow<Map<Long, DayFactors>> = dao.dayFactors()
        .map { list -> list.associateBy { it.day } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun saveDayFactors(factors: DayFactors) {
        viewModelScope.launch { dao.saveDayFactors(factors) }
    }

    /** Делает автоматическую копию прямо сейчас (для кнопки в настройках). */
    fun runAutoBackup(onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(AutoBackup.run(context)) }
    }

    /** Создаёт запись со временем "прямо сейчас" — вызывается по нажатию главной кнопки. */
    fun startNow(onCreated: (Long) -> Unit) = addAt(System.currentTimeMillis(), onCreated)

    fun addAt(time: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            // День с записью о боли не может быть отмечен как «без боли».
            dao.unmarkPainFree(time.toLocalDate().toEpochDay())
            val id = dao.insert(HeadacheEntry(startTime = time))
            PainWidget.updateAll(context)
            onCreated(id)
            // Запись уже сохранена со временем; погода и данные с часов дописываются следом, если включены.
            WeatherService.attach(context, id)
            HealthService.attach(context, id)
        }
    }

    /** Открытая запись без данных с часов: пробуем дописать (часы могли синхронизироваться позже). */
    fun ensureHealth(entry: HeadacheEntry) {
        val healthDone = !AppSettings.healthEnabled(context) ||
            (entry.sleepMinutes != null && entry.steps24h != null && entry.restingHeartRate != null)
        val cycleDone = !AppSettings.cycleEnabled(context) || entry.perimenstrual != null
        if (healthDone && cycleDone) return
        viewModelScope.launch { HealthService.attach(context, entry.id) }
    }

    fun refreshHealth(entryId: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(HealthService.attach(context, entryId, force = true)) }
    }

    /** При запуске дописывает данные с часов в недавние записи, у которых их ещё нет. */
    fun fillRecentHealth() {
        viewModelScope.launch { HealthService.fillRecent(context) }
    }

    fun markPainFree(day: Long) {
        viewModelScope.launch {
            dao.markPainFree(PainFreeDay(day))
            PainWidget.updateAll(context)
        }
    }

    fun unmarkPainFree(day: Long) {
        viewModelScope.launch {
            dao.unmarkPainFree(day)
            PainWidget.updateAll(context)
        }
    }

    /** Обновляет запомненное местоположение при запуске, пока приложение на экране и Android отдаёт координаты. */
    fun refreshLocation(force: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (!force && !AppSettings.weatherEnabled(context)) return@launch
            val coords = if (force) LocationHelper.refresh(context) else LocationHelper.best(context, maxAgeMs = 60 * 60 * 1000L)
            onDone(coords != null)
        }
    }

    /** Открытая запись без погоды: пробуем дописать её (например, если раньше не было сети). */
    fun ensureWeather(entry: HeadacheEntry) {
        // Записи без изменения давления (например, полученные только из запасного источника) пробуем дополнить.
        if (entry.pressureChange3h != null) return
        viewModelScope.launch { WeatherService.attach(context, entry.id) }
    }

    fun refreshWeather(entryId: Long, onDone: (WeatherOutcome) -> Unit) {
        viewModelScope.launch { onDone(WeatherService.attach(context, entryId, force = true)) }
    }

    fun save(entry: HeadacheEntry) {
        viewModelScope.launch {
            dao.update(entry)
            PainWidget.updateAll(context)
        }
    }

    fun endNow(entry: HeadacheEntry) = save(entry.copy(endTime = System.currentTimeMillis()))

    fun delete(entry: HeadacheEntry) {
        viewModelScope.launch {
            dao.delete(entry)
            PainWidget.updateAll(context)
        }
    }

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = Backup.toJson(dao.allOnce(), dao.painFreeDaysOnce().map { it.day }, dao.dayFactorsOnce())
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            onDone(ok)
        }
    }

    /** Добавляет записи из файла; записи с уже существующим временем начала пропускаются. */
    fun importBackup(uri: Uri, onDone: (ImportResult?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri) ?: error("No input stream")
                    val bytes = stream.use { it.readBytes() }
                    check(bytes.size <= MAX_BACKUP_BYTES) { "File is too large" }
                    val data = Backup.parse(bytes.toString(Charsets.UTF_8))
                    val existing = dao.allOnce().map { it.startTime }.toSet()
                    val fresh = data.entries.filter { it.startTime !in existing }.distinctBy { it.startTime }
                    dao.insertAll(fresh)
                    dao.markPainFreeAll(data.painFreeDays.map { PainFreeDay(it) })
                    dao.insertDayFactors(data.dayFactors)
                    ImportResult(added = fresh.size, skipped = data.entries.size - fresh.size)
                }.getOrNull()
            }
            PainWidget.updateAll(context)
            onDone(result)
        }
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 20_000_000
    }
}
