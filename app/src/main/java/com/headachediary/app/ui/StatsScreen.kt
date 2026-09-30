package com.headachediary.app.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.computeStats
import java.time.LocalDate

@Composable
fun StatsScreen(entries: List<HeadacheEntry>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(30) }
    var showReport by remember { mutableStateOf(false) }
    val since = System.currentTimeMillis() - days * DAY_MS
    val stats = computeStats(entries.filter { it.startTime >= since })

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = days == 30, onClick = { days = 30 }, label = { Text(stringResource(R.string.period_30)) })
            FilterChip(selected = days == 90, onClick = { days = 90 }, label = { Text(stringResource(R.string.period_90)) })
            FilterChip(selected = days == 365, onClick = { days = 365 }, label = { Text(stringResource(R.string.period_year)) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stringResource(R.string.stat_pain_days), stats.painDays.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.stat_migraine_days), stats.migraineDays.toString(), Modifier.weight(1f))
            StatTile(stringResource(R.string.stat_tension_days), stats.tensionDays.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                stringResource(R.string.stat_avg_intensity),
                stats.avgIntensity?.let { String.format(context.appLocale(), "%.1f", it) } ?: "—",
                Modifier.weight(1f),
            )
            StatTile(
                stringResource(R.string.stat_avg_duration),
                stats.avgDurationMs?.let { formatDuration(context, it) } ?: "—",
                Modifier.weight(1f),
            )
            StatTile(stringResource(R.string.stat_med_days), stats.medDays.toString(), Modifier.weight(1f))
        }

        if (days == 30 && stats.medDays >= 10) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    stringResource(R.string.warn_med_overuse),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        PainBars(entries)
        RankCard(
            stringResource(R.string.rank_triggers),
            stats.topTriggers.map { (t, n) -> context.getString(t.labelRes) to n },
        )
        RankCard(
            stringResource(R.string.rank_symptoms),
            stats.topSymptoms.map { (s, n) -> context.getString(s.labelRes) to n },
        )

        Button(
            onClick = { showReport = true },
            enabled = entries.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.btn_doctor_report)) }

        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showReport) DoctorReportDialog(entries) { showReport = false }
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
            Text(stringResource(R.string.bars_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.bars_subtitle),
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
                Text(stringResource(R.string.bars_30_ago), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.bars_today), style = MaterialTheme.typography.labelSmall)
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
                    stringResource(R.string.rank_no_data),
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
