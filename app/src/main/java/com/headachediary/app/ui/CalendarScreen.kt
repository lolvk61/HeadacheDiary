package com.headachediary.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)
private val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun CalendarScreen(
    entries: List<HeadacheEntry>,
    onOpen: (HeadacheEntry) -> Unit,
    onAddForDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    val month = YearMonth.now().plusMonths(offset.toLong())
    val byDay = remember(entries) { entries.groupBy { it.startTime.toLocalDate() } }
    val today = LocalDate.now()

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { offset-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Прошлый месяц")
            }
            Text(
                month.format(monthFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { offset++ }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
            }
        }

        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val lead = month.atDay(1).dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val rows = (lead + daysInMonth + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - lead + 1
                    if (day in 1..daysInMonth) {
                        val date = month.atDay(day)
                        DayCell(
                            day = day,
                            dayEntries = byDay[date].orEmpty(),
                            isToday = date == today,
                            isSelected = date.toEpochDay() == selectedEpochDay,
                            onClick = { selectedEpochDay = date.toEpochDay() },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }

        Row(
            Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(painColor(2), "слабая")
            LegendDot(painColor(5), "средняя")
            LegendDot(painColor(8), "сильная")
            Text("М — мигрень", style = MaterialTheme.typography.labelSmall)
        }

        val selected = selectedEpochDay?.let { LocalDate.ofEpochDay(it) }
        if (selected != null) {
            Text(formatDayHeader(selected), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val list = byDay[selected].orEmpty()
            if (list.isEmpty()) {
                Text(
                    "В этот день боли не отмечено.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { EntryCard(it, onClick = { onOpen(it) }) }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onAddForDate(selected) }) { Text("Добавить запись за этот день") }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    dayEntries: List<HeadacheEntry>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val hasPain = dayEntries.isNotEmpty()
    val maxIntensity = dayEntries.mapNotNull { it.intensity }.maxOrNull()
    val bg = if (hasPain) painColor(maxIntensity) else Color.Transparent
    val fg = if (hasPain) onPainColor(maxIntensity) else MaterialTheme.colorScheme.onSurface
    val hasMigraine = dayEntries.any { it.type == HeadacheType.MIGRAINE.name }

    var m = modifier.padding(2.dp).aspectRatio(1f).clip(shape).background(bg)
    m = when {
        isSelected -> m.border(2.dp, MaterialTheme.colorScheme.primary, shape)
        isToday -> m.border(1.dp, MaterialTheme.colorScheme.outline, shape)
        else -> m
    }
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.toString(), color = fg, style = MaterialTheme.typography.bodyMedium)
            if (hasMigraine) Text("М", color = fg, fontSize = 10.sp)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(" $label", style = MaterialTheme.typography.labelSmall)
    }
}
