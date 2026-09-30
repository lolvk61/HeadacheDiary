package com.headachediary.app.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.Symptom
import com.headachediary.app.data.Trigger
import com.headachediary.app.data.toKeySet
import java.time.LocalDate
import java.util.Locale

@Composable
fun StatsScreen(entries: List<HeadacheEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(30) }
    val since = System.currentTimeMillis() - days * DAY_MS
    val list = entries.filter { it.startTime >= since }

    val painDays = list.map { it.startTime.toLocalDate() }.distinct().size
    val migraineDays = list.filter { it.type == HeadacheType.MIGRAINE.name }
        .map { it.startTime.toLocalDate() }.distinct().size
    val tensionDays = list.filter { it.type == HeadacheType.TENSION.name }
        .map { it.startTime.toLocalDate() }.distinct().size
    val medDays = list.filter { it.medication.isNotBlank() }
        .map { it.startTime.toLocalDate() }.distinct().size
    val avgIntensity = list.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average()
    val avgDuration = list.mapNotNull { e -> e.endTime?.let { it - e.startTime } }
        .takeIf { it.isNotEmpty() }?.average()?.toLong()

    fun topKeys(selector: (HeadacheEntry) -> String, label: (String) -> String?): List<Pair<String, Int>> =
        list.flatMap { selector(it).toKeySet() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .mapNotNull { (k, v) -> label(k)?.let { it to v } }
            .take(5)

    val topTriggers = topKeys({ it.triggers }) { k -> Trigger.entries.firstOrNull { it.name == k }?.label }
    val topSymptoms = topKeys({ it.symptoms }) { k -> Symptom.entries.firstOrNull { it.name == k }?.label }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Статистика", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text("30 дней") })
            FilterChip(selected = days == 90, onClick = { days = 90 }, label = { Text("90 дней") })
            FilterChip(selected = days == 365, onClick = { days = 365 }, label = { Text("Год") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Дней с болью", painDays.toString(), Modifier.weight(1f))
            StatTile("Дней мигрени", migraineDays.toString(), Modifier.weight(1f))
            StatTile("Обычная боль", tensionDays.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                "Средняя сила",
                avgIntensity?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                Modifier.weight(1f),
            )
            StatTile("Средняя длительность", avgDuration?.let { formatDuration(it) } ?: "—", Modifier.weight(1f))
            StatTile("Дней с лекарством", medDays.toString(), Modifier.weight(1f))
        }

        if (days == 30 && medDays >= 10) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Обезболивающие принимались 10 и более дней за месяц. Частый приём лекарств " +
                        "сам может усиливать головную боль — стоит обсудить это с врачом.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        PainBars(entries)
        RankCard("Частые провокаторы", topTriggers)
        RankCard("Частые симптомы", topSymptoms)

        Button(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Дневник головной боли")
                    putExtra(Intent.EXTRA_TEXT, buildCsv(entries))
                }
                context.startActivity(Intent.createChooser(send, "Отправить дневник"))
            },
            enabled = entries.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Экспорт для врача (все записи)") }

        Text(
            "Приложение ведёт дневник и не заменяет консультацию врача.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Столбики по дням за последние 30 дней: высота и цвет зависят от силы боли. */
@Composable
private fun PainBars(entries: List<HeadacheEntry>) {
    val today = LocalDate.now()
    val byDay = entries.groupBy { it.startTime.toLocalDate() }
    // null — боли не было, 0 — боль без указанной силы
    val values: List<Int?> = (29 downTo 0).map { back ->
        byDay[today.minusDays(back.toLong())]?.let { list -> list.mapNotNull { it.intensity }.maxOrNull() ?: 0 }
    }
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("Сила боли по дням", style = MaterialTheme.typography.titleMedium)
            Text(
                "Последние 30 дней",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val gap = 4.dp.toPx()
                val barWidth = (size.width - gap * (values.size - 1)) / values.size
                values.forEachIndexed { i, v ->
                    val x = i * (barWidth + gap)
                    if (v == null) {
                        val h = 4.dp.toPx()
                        drawRoundRect(emptyColor, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(2.dp.toPx()))
                    } else {
                        val h = if (v == 0) size.height * 0.3f else size.height * v / 10f
                        drawRoundRect(
                            painColor(v.takeIf { it > 0 }),
                            Offset(x, size.height - h),
                            Size(barWidth, h),
                            CornerRadius(3.dp.toPx()),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("30 дн. назад", style = MaterialTheme.typography.labelSmall)
                Text("сегодня", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RankCard(title: String, items: List<Pair<String, Int>>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text(
                    "Пока нет данных за выбранный период.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items.forEach { (label, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
