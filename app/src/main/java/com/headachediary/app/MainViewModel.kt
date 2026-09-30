package com.headachediary.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.Backup
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.widget.PainWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportResult(val added: Int, val skipped: Int)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context = app.applicationContext
    private val dao = AppDatabase.get(app).dao()

    val entries: StateFlow<List<HeadacheEntry>> = dao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Создаёт запись со временем "прямо сейчас" — вызывается по нажатию главной кнопки. */
    fun startNow(onCreated: (Long) -> Unit) = addAt(System.currentTimeMillis(), onCreated)

    fun addAt(time: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = dao.insert(HeadacheEntry(startTime = time))
            PainWidget.updateAll(context)
            onCreated(id)
        }
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
                    val json = Backup.toJson(dao.allOnce())
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
                    val parsed = Backup.parse(bytes.toString(Charsets.UTF_8))
                    val existing = dao.allOnce().map { it.startTime }.toSet()
                    val fresh = parsed.filter { it.startTime !in existing }.distinctBy { it.startTime }
                    dao.insertAll(fresh)
                    ImportResult(added = fresh.size, skipped = parsed.size - fresh.size)
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
