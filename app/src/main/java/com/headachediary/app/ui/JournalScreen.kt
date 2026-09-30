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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headachediary.app.R
import com.headachediary.app.data.DayFactors
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.PressureRelevance
import com.headachediary.app.data.pressureRelevance
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.weather.PressureOutlook
import com.headachediary.app.weather.WeatherService
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle as DateTextStyle

@Composable
fun JournalScreen(
    entries: List<HeadacheEntry>,
    painFreeDays: Set<Long>,
    dayFactors: Map<Long, DayFactors>,
    onPainNow: () -> Unit,
    onOpen: (HeadacheEntry) -> Unit,
    onEnd: (HeadacheEntry) -> Unit,
    onMarkPainFree: (Long) -> Unit,
    onUnmarkPainFree: (Long) -> Unit,
    onSaveFactors: (DayFactors) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val weatherEnabled = AppSettings.weatherEnabled(context)
    // Прогноз давления на ближайшие сутки: запрашивается в сети, поэтому появляется чуть позже остального.
    val outlook by produceState<PressureOutlook?>(null, weatherEnabled) {
        value = if (weatherEnabled) WeatherService.pressureOutlook(context) else null
    }
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
    val daysSincePain = entries.firstOrNull()?.let { ChronoUnit.DAYS.between(it.startTime.toLocalDate(), today) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    formatDayHeader(context, today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.journal_greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item { PainHero(ongoing, now, onPainNow, { ongoing?.let(onOpen) }, { ongoing?.let(onEnd) }) }

        // Если сегодня записей нет, предлагаем отметить день без боли (или снять отметку).
        if (byDay[today].isNullOrEmpty()) {
            item {
                TodayPainFreeCard(
                    marked = today.toEpochDay() in painFreeDays,
                    onMark = { onMarkPainFree(today.toEpochDay()) },
                    onUndo = { onUnmarkPainFree(today.toEpochDay()) },
                )
            }
        }

        item { FactorsCard(today.toEpochDay(), dayFactors[today.toEpochDay()], onSaveFactors) }

        outlook?.let { o -> item { OutlookCard(o, pressureRelevance(entries)) } }

        if (entries.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.tile_pain_days_30), painDays.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.tile_migraine_days), migraineDays.toString(), Modifier.weight(1f))
                    StatTile(
                        stringResource(R.string.tile_pain_free_days),
                        daysSincePain?.toString() ?: "—",
                        Modifier.weight(1f),
                    )
                }
            }
            item { WeekStrip(today, byDay, painFreeDays) }
            item {
                Text(
                    stringResource(R.string.journal_history),
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
                    formatDayHeader(context, date),
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
    val context = LocalContext.current
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
                    Text(
                        stringResource(R.string.hero_pain_title),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.hero_pain_subtitle),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.hero_ongoing_title),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        R.string.hero_ongoing_subtitle,
                        formatTime(ongoing.startTime),
                        formatDuration(context, now - ongoing.startTime),
                    ),
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
                    ) { Text(stringResource(R.string.hero_end)) }
                    OutlinedButton(
                        onClick = onOpen,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    ) { Text(stringResource(R.string.hero_details)) }
                }
            }
        }
    }
}

/** Плитка «Сегодня без боли?» с одной кнопкой; после отметки показывает подтверждение и «Отменить». */
@Composable
private fun TodayPainFreeCard(marked: Boolean, onMark: () -> Unit, onUndo: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = if (marked) {
            CardDefaults.cardColors(containerColor = PainFreeColor.copy(alpha = 0.18f))
        } else {
            softCardColors()
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(if (marked) R.string.journal_today_marked else R.string.journal_today_ask),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = if (marked) onUndo else onMark) {
                Text(stringResource(if (marked) R.string.undo else R.string.journal_mark_pain_free))
            }
        }
    }
}

/** Прогноз давления: предупреждение о перепаде или спокойное «давление стабильно». */
@Composable
private fun OutlookCard(outlook: PressureOutlook, relevance: PressureRelevance) {
    val context = LocalContext.current
    val swing = outlook.isSharp
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = if (swing && !relevance.unlikely) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else {
            softCardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (swing) R.string.outlook_swing_title else R.string.outlook_calm_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (swing) outlookMessage(context, outlook, relevance) else stringResource(R.string.outlook_calm_text),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, byDay: Map<LocalDate, List<HeadacheEntry>>, painFreeDays: Set<Long>) {
    val locale = LocalContext.current.appLocale()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.week_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (back in 6 downTo 0) {
                    val date = today.minusDays(back.toLong())
                    val list = byDay[date].orEmpty()
                    val maxIntensity = list.mapNotNull { it.intensity }.maxOrNull()
                    val marked = list.isEmpty() && date.toEpochDay() in painFreeDays
                    val bg = when {
                        list.isNotEmpty() -> painColor(maxIntensity)
                        marked -> PainFreeColor
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val fg = when {
                        list.isNotEmpty() -> onPainColor(maxIntensity)
                        marked -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale),
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
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
