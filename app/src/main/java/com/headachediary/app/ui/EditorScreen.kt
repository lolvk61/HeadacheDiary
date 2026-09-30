package com.headachediary.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.MedHelp
import com.headachediary.app.data.Symptom
import com.headachediary.app.data.SymptomGroup
import com.headachediary.app.data.Trigger
import com.headachediary.app.data.toKeySet
import com.headachediary.app.data.toggle
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.weather.WeatherOutcome
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    entry: HeadacheEntry,
    onSave: (HeadacheEntry) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onEnsureWeather: () -> Unit,
    onRefreshWeather: ((WeatherOutcome) -> Unit) -> Unit,
    onEnsureHealth: () -> Unit,
    onRefreshHealth: ((Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val weatherEnabled = AppSettings.weatherEnabled(context)
    val healthEnabled = AppSettings.healthEnabled(context)
    var refreshingWeather by remember { mutableStateOf(false) }
    var refreshingHealth by remember { mutableStateOf(false) }

    // Если запись открыта без погоды или данных с часов (например, не было сети), пробуем дописать один раз.
    LaunchedEffect(entry.id) {
        onEnsureWeather()
        onEnsureHealth()
    }

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
                title = { Text(stringResource(R.string.editor_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = ::saveAndClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.navigationBarsPadding()) {
                    Button(onClick = ::saveAndClose, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(stringResource(R.string.section_time)) {
                TimeRow(stringResource(R.string.time_started), formatDateTime(context, start)) {
                    pickDateTime(context, start) { start = it }
                }
                TimeRow(
                    stringResource(R.string.time_ended),
                    end?.let { formatDateTime(context, it) } ?: stringResource(R.string.time_still_hurts),
                ) {
                    pickDateTime(context, end ?: System.currentTimeMillis()) { end = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { end = System.currentTimeMillis() }) {
                        Text(stringResource(R.string.btn_ended_now))
                    }
                    if (end != null) {
                        TextButton(onClick = { end = null }) { Text(stringResource(R.string.btn_still_hurts)) }
                    }
                }
                val e = end
                if (e != null && e < start) {
                    Text(
                        stringResource(R.string.err_end_before_start),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (e != null) {
                    Text(
                        stringResource(R.string.duration_label, formatDuration(context, e - start)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (weatherEnabled || entry.pressure != null || entry.temperature != null) {
                SectionCard(stringResource(R.string.section_weather)) {
                    val hasWeather = entry.pressure != null || entry.temperature != null
                    if (hasWeather) {
                        entry.temperature?.let {
                            WeatherRow(stringResource(R.string.weather_temperature), formatTemperature(context, it))
                        }
                        entry.weatherCode?.let {
                            WeatherRow(stringResource(R.string.weather_conditions), stringResource(weatherLabelRes(it)))
                        }
                        entry.pressure?.let {
                            WeatherRow(stringResource(R.string.weather_pressure), formatPressure(context, it))
                        }
                        entry.pressureChange3h?.let {
                            WeatherRow(stringResource(R.string.weather_change_3h), formatPressureChange(context, it))
                        }
                        entry.pressureChange24h?.let {
                            WeatherRow(stringResource(R.string.weather_change_24h), formatPressureChange(context, it))
                        }
                        entry.humidity?.let {
                            WeatherRow(stringResource(R.string.weather_humidity), "$it%")
                        }
                    } else {
                        Text(
                            stringResource(R.string.weather_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (weatherEnabled) {
                        TextButton(
                            enabled = !refreshingWeather,
                            onClick = {
                                refreshingWeather = true
                                onRefreshWeather { outcome ->
                                    refreshingWeather = false
                                    val message = when (outcome) {
                                        WeatherOutcome.NO_LOCATION -> R.string.weather_no_location
                                        WeatherOutcome.NO_DATA -> R.string.weather_no_network
                                        else -> null
                                    }
                                    if (message != null) {
                                        Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                        ) {
                            Text(
                                stringResource(if (refreshingWeather) R.string.weather_loading else R.string.weather_refresh),
                            )
                        }
                    }
                }
            }

            val hasHealth = entry.sleepMinutes != null || entry.steps24h != null || entry.restingHeartRate != null
            if (healthEnabled || hasHealth) {
                SectionCard(stringResource(R.string.section_health)) {
                    if (hasHealth) {
                        entry.sleepMinutes?.let {
                            WeatherRow(stringResource(R.string.health_sleep), formatDuration(context, it * 60_000L))
                        }
                        entry.steps24h?.let {
                            WeatherRow(stringResource(R.string.health_steps), String.format(context.appLocale(), "%,d", it))
                        }
                        entry.restingHeartRate?.let {
                            WeatherRow(stringResource(R.string.health_resting_hr), stringResource(R.string.health_bpm, it))
                        }
                    } else {
                        Text(
                            stringResource(R.string.health_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (healthEnabled) {
                        TextButton(
                            enabled = !refreshingHealth,
                            onClick = {
                                refreshingHealth = true
                                onRefreshHealth { ok ->
                                    refreshingHealth = false
                                    if (!ok) {
                                        Toast.makeText(context, context.getString(R.string.health_failed), Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(if (refreshingHealth) R.string.weather_loading else R.string.health_refresh))
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.section_type)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeadacheType.entries.forEach {
                        FilterChip(
                            selected = type == it,
                            onClick = { type = it },
                            label = { Text(stringResource(it.labelRes)) },
                        )
                    }
                }
            }

            SectionCard(stringResource(R.string.section_intensity)) {
                val color = painColor(intensity)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            intensity?.toString() ?: "?",
                            color = onPainColor(intensity),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            stringResource(intensityLabelRes(intensity)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(if (intensity == null) R.string.intensity_hint else R.string.intensity_of_10),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Slider(
                    value = (intensity ?: 5).toFloat(),
                    onValueChange = { intensity = it.roundToInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }

            SectionCard(stringResource(R.string.section_symptoms)) {
                SymptomGroup.entries.forEach { group ->
                    Text(
                        stringResource(group.titleRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Symptom.entries.filter { it.group == group }.forEach { s ->
                            FilterChip(
                                selected = s.name in symptoms,
                                onClick = { symptoms = symptoms.toggle(s.name) },
                                label = { Text(stringResource(s.labelRes)) },
                            )
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.section_triggers)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Trigger.entries.forEach { t ->
                        FilterChip(
                            selected = t.name in triggers,
                            onClick = { triggers = triggers.toggle(t.name) },
                            label = { Text(stringResource(t.labelRes)) },
                        )
                    }
                }
            }

            SectionCard(stringResource(R.string.section_medication)) {
                OutlinedTextField(
                    value = medication,
                    onValueChange = { medication = it },
                    label = { Text(stringResource(R.string.med_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (medication.isNotBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MedHelp.entries.forEach { h ->
                            FilterChip(
                                selected = medHelped == h.name,
                                onClick = { medHelped = if (medHelped == h.name) "" else h.name },
                                label = { Text(stringResource(h.labelRes)) },
                            )
                        }
                    }
                }
            }

            SectionCard(stringResource(R.string.section_notes)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WeatherRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.time_change),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
