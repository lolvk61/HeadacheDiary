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
import androidx.compose.runtime.produceState
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
import com.headachediary.app.data.DayFactor
import com.headachediary.app.data.DayFactors
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.MIN_DAYS_PER_GROUP
import com.headachediary.app.data.SHORT_SLEEP_MINUTES
import com.headachediary.app.data.computeFactorResults
import com.headachediary.app.data.computeStats
import com.headachediary.app.health.HealthBaseline
import com.headachediary.app.health.HealthService
import com.headachediary.app.data.hasSharpPressureChange
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.weather.WeatherClient
import com.headachediary.app.weather.WeatherService
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    entries: List<HeadacheEntry>,
    painFreeDays: Set<Long>,
    dayFactors: Map<Long, DayFactors>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(30) }
    var showReport by remember { mutableStateOf(false) }
    val since = System.currentTimeMillis() - days * DAY_MS
    val stats = computeStats(entries.filter { it.startTime >= since })
    val markedPainFree = painFreeDays.count { it >= since.toLocalDate().toEpochDay() }

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

        Text(
            stringResource(R.string.stat_tracked, stats.painDays + markedPainFree, stats.painDays, markedPainFree),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
        WeatherCard(entries, windowDays = minOf(days, WeatherClient.MAX_PAST_DAYS))
        HealthCard(entries, windowDays = minOf(days, WeatherClient.MAX_PAST_DAYS))
        CycleCard(entries, windowDays = minOf(days, WeatherClient.MAX_PAST_DAYS))
        FactorsStatsCard(entries, dayFactors, days)
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

    if (showReport) DoctorReportDialog(entries, painFreeDays) { showReport = false }
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

/** Доля «обычных» часов с перепадом давления; null внутри — данные недоступны. */
private data class Baseline(val share: Double?)

/**
 * Сравнивает погоду в моменты приступов с обычной погодой в том же месте за тот же период:
 * только так видно, случаются ли приступы при перепадах давления чаще, чем «по случайности».
 */
@Composable
private fun WeatherCard(entries: List<HeadacheEntry>, windowDays: Int) {
    val context = LocalContext.current
    val enabled = AppSettings.weatherEnabled(context)
    val since = System.currentTimeMillis() - windowDays * DAY_MS
    val inWindow = entries.filter { it.startTime >= since }
    val withWeather = inWindow.filter { it.pressureChange3h != null }
    if (!enabled && inWindow.none { it.pressure != null }) return

    val baseline by produceState<Baseline?>(null, windowDays, enabled, withWeather.size) {
        value = null
        value = Baseline(if (enabled && withWeather.isNotEmpty()) WeatherService.baselineShare(context, windowDays) else null)
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.weather_stats_title), style = MaterialTheme.typography.titleMedium)
            if (withWeather.isEmpty()) {
                Text(
                    stringResource(R.string.weather_stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val sharp = withWeather.count { it.hasSharpPressureChange() }
            val attackShare = sharp.toDouble() / withWeather.size
            val avgPressure = withWeather.mapNotNull { it.pressure }.average()
            val baselineShare = baseline?.share

            Text(
                stringResource(R.string.weather_stats_attacks, withWeather.size, windowDays),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!avgPressure.isNaN()) {
                Text(
                    stringResource(R.string.weather_stats_avg_pressure, formatPressure(context, avgPressure)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.weather_stats_sharp, sharp, withWeather.size, (attackShare * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                baseline == null -> Text(
                    stringResource(R.string.weather_stats_baseline_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                baselineShare != null -> Text(
                    stringResource(R.string.weather_stats_baseline, (baselineShare * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val verdict = when {
                withWeather.size < MIN_ATTACKS_FOR_VERDICT -> R.string.weather_stats_verdict_few
                baselineShare == null -> null
                attackShare >= 0.3 && attackShare >= 1.5 * baselineShare -> R.string.weather_stats_verdict_more
                else -> R.string.weather_stats_verdict_none
            }
            if (verdict != null) {
                Text(
                    stringResource(verdict),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                stringResource(R.string.weather_stats_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MIN_ATTACKS_FOR_VERDICT = 5

/** Доля дней околоменструального окна за период; null — данных о цикле нет. */
private data class CycleBaselineState(val share: Double?)

/** Приступы в околоменструальном окне против доли таких дней в обычном цикле (даты берутся из Health Connect). */
@Composable
private fun CycleCard(entries: List<HeadacheEntry>, windowDays: Int) {
    val context = LocalContext.current
    val enabled = AppSettings.cycleEnabled(context)
    val since = System.currentTimeMillis() - windowDays * DAY_MS
    val withCycle = entries.filter { it.startTime >= since && it.perimenstrual != null }
    if (!enabled && withCycle.isEmpty()) return

    val baselineState by produceState<CycleBaselineState?>(null, windowDays, enabled, withCycle.size) {
        value = null
        value = CycleBaselineState(if (enabled) HealthService.cycleBaseline(context, windowDays) else null)
    }
    val usual = baselineState?.share

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.cycle_stats_title), style = MaterialTheme.typography.titleMedium)
            if (withCycle.isEmpty()) {
                Text(
                    stringResource(R.string.cycle_stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val inWindow = withCycle.count { it.perimenstrual == true }
            val share = inWindow.toDouble() / withCycle.size
            Text(
                stringResource(R.string.cycle_stats_attacks, inWindow, withCycle.size, (share * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (usual != null) {
                Text(
                    stringResource(R.string.cycle_stats_baseline, (usual * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val verdict = when {
                withCycle.size < MIN_ATTACKS_FOR_VERDICT -> R.string.cycle_verdict_few
                usual == null -> null
                share >= 0.4 && share >= 1.5 * usual -> R.string.cycle_verdict_more
                else -> R.string.cycle_verdict_none
            }
            if (verdict != null) {
                Text(
                    stringResource(verdict),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                stringResource(R.string.cycle_stats_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Сравнение дней с болью и без неё по отмеченным факторам дня (стресс, кофеин, алкоголь, вода, еда). */
@Composable
private fun FactorsStatsCard(entries: List<HeadacheEntry>, dayFactors: Map<Long, DayFactors>, days: Int) {
    val sinceDay = (System.currentTimeMillis() - days * DAY_MS).toLocalDate().toEpochDay()
    val logged = dayFactors.values.filter { it.day >= sinceDay }
    val painDays = entries.map { it.startTime.toLocalDate().toEpochDay() }.toSet()
    val results = computeFactorResults(logged, painDays).take(3)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.factors_stats_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.factors_stats_logged, logged.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (results.isEmpty()) {
                Text(
                    stringResource(R.string.factors_stats_no_data, MIN_DAYS_PER_GROUP),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            results.forEach { r ->
                Text(
                    stringResource(
                        R.string.factors_stats_row,
                        stringResource(factorLabelRes(r.factor)),
                        (r.shareWith * 100).roundToInt(),
                        (r.shareWithout * 100).roundToInt(),
                        r.daysWith,
                        r.daysWithout,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.factors_stats_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@androidx.annotation.StringRes
private fun factorLabelRes(factor: DayFactor): Int = when (factor) {
    DayFactor.HIGH_STRESS -> R.string.factor_high_stress
    DayFactor.MODERATE_STRESS -> R.string.factor_moderate_stress
    DayFactor.CAFFEINE -> R.string.factor_caffeine
    DayFactor.ALCOHOL -> R.string.factors_alcohol
    DayFactor.LOW_WATER -> R.string.factors_low_water
    DayFactor.SKIPPED_MEAL -> R.string.factors_skipped_meal
}

/** Результат расчёта «обычного фона»; null внутри — данных нет. */
private data class HealthBaselineState(val value: HealthBaseline?)

/**
 * Сравнивает сон, шаги и пульс в покое перед приступами с обычными значениями за тот же период
 * (данные с часов из Health Connect).
 */
@Composable
private fun HealthCard(entries: List<HeadacheEntry>, windowDays: Int) {
    val context = LocalContext.current
    val enabled = AppSettings.healthEnabled(context)
    val since = System.currentTimeMillis() - windowDays * DAY_MS
    val inWindow = entries.filter { it.startTime >= since }
    val sleeps = inWindow.mapNotNull { it.sleepMinutes }
    val steps = inWindow.mapNotNull { it.steps24h }
    val hearts = inWindow.mapNotNull { it.restingHeartRate }
    if (!enabled && sleeps.isEmpty() && steps.isEmpty() && hearts.isEmpty()) return

    val baselineState by produceState<HealthBaselineState?>(null, windowDays, enabled, sleeps.size) {
        value = null
        value = HealthBaselineState(if (enabled) HealthService.baseline(context, windowDays) else null)
    }
    val baseline = baselineState?.value

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = softCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.health_stats_title), style = MaterialTheme.typography.titleMedium)
            if (sleeps.isEmpty() && steps.isEmpty() && hearts.isEmpty()) {
                Text(
                    stringResource(R.string.health_stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            if (sleeps.isNotEmpty()) {
                val short = sleeps.count { it < SHORT_SLEEP_MINUTES }
                val shortShare = short.toDouble() / sleeps.size
                Text(
                    stringResource(
                        R.string.health_stats_avg_sleep,
                        formatDuration(context, sleeps.average().toLong() * 60_000L),
                        sleeps.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.health_stats_short_sleep, short, sleeps.size, (shortShare * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                baseline?.shortSleepShare?.let {
                    Text(
                        stringResource(R.string.health_stats_short_baseline, (it * 100).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val usualShort = baseline?.shortSleepShare
                val verdict = when {
                    sleeps.size < MIN_ATTACKS_FOR_VERDICT -> R.string.health_verdict_few
                    usualShort == null -> null
                    shortShare >= 0.3 && shortShare >= 1.5 * usualShort -> R.string.health_verdict_more
                    else -> R.string.health_verdict_none
                }
                if (verdict != null) {
                    Text(
                        stringResource(verdict),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (steps.isNotEmpty()) {
                val avg = steps.average().roundToInt()
                Text(
                    stringResource(
                        R.string.health_stats_steps,
                        String.format(context.appLocale(), "%,d", avg),
                        baseline?.avgSteps?.let { String.format(context.appLocale(), "%,d", it) } ?: "—",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (hearts.isNotEmpty()) {
                Text(
                    stringResource(
                        R.string.health_stats_hr,
                        hearts.average().roundToInt(),
                        baseline?.avgRestingHeartRate?.toString() ?: "—",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.health_stats_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
