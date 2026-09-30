package com.headachediary.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.HeadacheEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    val entries: StateFlow<List<HeadacheEntry>> = dao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Создаёт запись со временем "прямо сейчас" — вызывается по нажатию главной кнопки. */
    fun startNow(onCreated: (Long) -> Unit) = addAt(System.currentTimeMillis(), onCreated)

    fun addAt(time: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = dao.insert(HeadacheEntry(startTime = time))
            onCreated(id)
        }
    }

    fun save(entry: HeadacheEntry) {
        viewModelScope.launch { dao.update(entry) }
    }

    fun endNow(entry: HeadacheEntry) = save(entry.copy(endTime = System.currentTimeMillis()))

    fun delete(entry: HeadacheEntry) {
        viewModelScope.launch { dao.delete(entry) }
    }
}
