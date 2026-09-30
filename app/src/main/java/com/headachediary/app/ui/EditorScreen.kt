package com.headachediary.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.MedHelp
import com.headachediary.app.data.Symptom
import com.headachediary.app.data.SymptomGroup
import com.headachediary.app.data.Trigger
import com.headachediary.app.data.toKeySet
import com.headachediary.app.data.toggle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    entry: HeadacheEntry,
    onSave: (HeadacheEntry) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var start by remember(entry.id) { mutableLongStateOf(entry.startTime) }
    var end by remember(entry.id) { mutableStateOf(entry.endTime) }
    var type by remember(entry.id) { mutableStateOf(HeadacheType.fromKey(entry.type)) }
    var intensity by remember(entry.id) { mutableStateOf(entry.intensity) }
    var symptoms by remember(entry.id) { mutableStateOf(entry.symptoms.toKeySet()) }
    var triggers by remember(entry.id) { mutableStateOf(entry.triggers.toKeySet()) }
    var medication by remember(entry.id) { mutableStateOf(entry.medication) }
    var medHelped by remember(entry.id) { mutableStateOf(entry.medicationHelped) }
    var notes by remember(entry.id) { mutableStateOf(entry.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build() = entry.copy(
        startTime = start,
        endTime = end?.takeIf { it >= start },
        type = type.name,
        intensity = intensity,
        symptoms = symptoms.joinToString(","),
        triggers = triggers.joinToString(","),
        medication = medication.trim(),
        medicationHelped = medHelped,
        notes = notes.trim(),
    )

    fun saveAndClose() {
        onSave(build())
        onClose()
    }

    BackHandler { saveAndClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Запись о боли") },
                navigationIcon = {
                    IconButton(onClick = ::saveAndClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Время") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickDateTime(context, start) { start = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text("Началась: ${formatDateTime(start)}") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickDateTime(context, end ?: System.currentTimeMillis()) { end = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text(end?.let { "Закончилась: ${formatDateTime(it)}" } ?: "Закончилась: не указано") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { end = System.currentTimeMillis() }) { Text("Прошла сейчас") }
                    if (end != null) TextButton(onClick = { end = null }) { Text("Ещё болит") }
                }
                val e = end
                if (e != null && e < start) {
                    Text(
                        "Время окончания раньше начала — оно не будет сохранено.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (e != null) {
                    Text("Длительность: ${formatDuration(e - start)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Section("Тип боли") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeadacheType.entries.forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.label) })
                    }
                }
            }

            Section("Сила боли: ${intensity?.let { "$it из 10" } ?: "не указана"}") {
                Slider(
                    value = (intensity ?: 5).toFloat(),
                    onValueChange = { intensity = it.roundToInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }

            Section("Симптомы") {
                SymptomGroup.entries.forEach { group ->
                    Text(group.title, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Symptom.entries.filter { it.group == group }.forEach { s ->
                            FilterChip(
                                selected = s.name in symptoms,
                                onClick = { symptoms = symptoms.toggle(s.name) },
                                label = { Text(s.label) },
                            )
                        }
                    }
                }
            }

            Section("Возможные провокаторы") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Trigger.entries.forEach { t ->
                        FilterChip(
                            selected = t.name in triggers,
                            onClick = { triggers = triggers.toggle(t.name) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }

            Section("Лекарство") {
                OutlinedTextField(
                    value = medication,
                    onValueChange = { medication = it },
                    label = { Text("Что принимали и в какой дозе") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (medication.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedHelp.entries.forEach { h ->
                            FilterChip(
                                selected = medHelped == h.name,
                                onClick = { medHelped = if (medHelped == h.name) "" else h.name },
                                label = { Text(h.label) },
                            )
                        }
                    }
                }
            }

            Section("Заметки") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            Button(onClick = ::saveAndClose, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить запись?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
