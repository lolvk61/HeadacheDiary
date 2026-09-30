package com.headachediary.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as DateTextStyle

@Composable
fun JournalScreen(
    entries: List<HeadacheEntry>,
    onPainNow: () -> Unit,
    onOpen: (HeadacheEntry) -> Unit,
    onEnd: (HeadacheEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val today = now.toLocalDate()
    val ongoing = entries.firstOrNull { it.isOngoing(now) }
    val byDay = remember(entries) { entries.groupBy { it.startTime.toLocalDate() } }

    val last30 = entries.filter { it.startTime >= now - 30 * DAY_MS }
    val painDays = last30.map { it.startTime.toLocalDate() }.distinct().size
    val migraineDays = last30.filter { it.type == HeadacheType.MIGRAINE.name }
        .map { it.startTime.toLocalDate() }.distinct().size
    val painFreeDays = entries.firstOrNull()?.let { ChronoUnit.DAYS.between(it.startTime.toLocalDate(), today) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    formatDayHeader(today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Как вы себя чувствуете?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item { PainHero(ongoing, now, onPainNow, { ongoing?.let(onOpen) }, { ongoing?.let(onEnd) }) }

        if (entries.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Дней боли за 30 дн.", painDays.toString(), Modifier.weight(1f))
                    StatTile("Дней мигрени", migraineDays.toString(), Modifier.weight(1f))
                    StatTile("Дней без боли", painFreeDays?.toString() ?: "—", Modifier.weight(1f))
                }
            }
            item { WeekStrip(today, byDay) }
            item {
                Text(
                    "История",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            item { EmptyState() }
        }

        byDay.forEach { (date, list) ->
            item(key = "header-$date") {
                Text(
                    formatDayHeader(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(list, key = { it.id }) { entry ->
                EntryCard(entry, onClick = { onOpen(entry) })
            }
        }
    }
}

@Composable
private fun PainHero(
    ongoing: HeadacheEntry?,
    now: Long,
    onPainNow: () -> Unit,
    onOpen: () -> Unit,
    onEnd: () -> Unit,
) {
    val colors = if (ongoing == null) {
        listOf(Color(0xFFE85D5D), Color(0xFFB3261E))
    } else {
        listOf(Color(0xFFB3261E), Color(0xFF5E1414))
    }
    var box = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(colors))
    if (ongoing == null) box = box.clickable(onClick = onPainNow)

    Box(box.padding(20.dp)) {
        if (ongoing == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Болит голова", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Нажмите — время запишется сразу",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Приступ идёт", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(
                    "С ${formatTime(ongoing.startTime)} · уже ${formatDuration(now - ongoing.startTime)}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onEnd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFB3261E),
                        ),
                    ) { Text("Боль прошла") }
                    OutlinedButton(
                        onClick = onOpen,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    ) { Text("Добавить детали") }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, byDay: Map<LocalDate, List<HeadacheEntry>>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("Последние 7 дней", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (back in 6 downTo 0) {
                    val date = today.minusDays(back.toLong())
                    val list = byDay[date].orEmpty()
                    val maxIntensity = list.mapNotNull { it.intensity }.maxOrNull()
                    val bg = if (list.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else painColor(maxIntensity)
                    val fg = if (list.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else onPainColor(maxIntensity)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, ruLocale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(38.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                            Text(date.dayOfMonth.toString(), color = fg, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Пока нет записей", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Когда начнётся боль, нажмите красную кнопку выше — время сохранится автоматически. " +
                "Здесь появятся история, статистика и календарь.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
