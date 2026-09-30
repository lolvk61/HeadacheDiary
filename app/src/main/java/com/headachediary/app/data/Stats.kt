package com.headachediary.app.data

import com.headachediary.app.ui.toLocalDate

data class PeriodStats(
    val attacks: Int,
    val painDays: Int,
    val migraineDays: Int,
    val tensionDays: Int,
    val medDays: Int,
    val avgIntensity: Double?,
    val avgDurationMs: Long?,
    val topTriggers: List<Pair<Trigger, Int>>,
    val topSymptoms: List<Pair<Symptom, Int>>,
)

/** Насколько перепады давления связаны с приступами по записям пользователя. */
data class PressureRelevance(val sharp: Int, val total: Int) {
    /** Достаточно ли записей с погодой, чтобы говорить о личной связи. */
    val known: Boolean get() = total >= 5
    val share: Double get() = if (total == 0) 0.0 else sharp.toDouble() / total

    /** Перепады, судя по записям, почти не предшествуют приступам — предупреждать о них незачем. */
    val unlikely: Boolean get() = known && share < 0.15
}

fun pressureRelevance(entries: List<HeadacheEntry>): PressureRelevance {
    val withData = entries.filter { it.pressureChange3h != null }
    return PressureRelevance(withData.count { it.hasSharpPressureChange() }, withData.size)
}

/** Одно условие дня (например, «сильный стресс») и как часто в такие дни бывала боль. */
enum class DayFactor { HIGH_STRESS, MODERATE_STRESS, CAFFEINE, ALCOHOL, LOW_WATER, SKIPPED_MEAL }

data class FactorResult(
    val factor: DayFactor,
    val daysWith: Int,
    val painDaysWith: Int,
    val daysWithout: Int,
    val painDaysWithout: Int,
) {
    val shareWith: Double get() = painDaysWith.toDouble() / daysWith
    val shareWithout: Double get() = painDaysWithout.toDouble() / daysWithout
}

/** Минимум дней в каждой группе, чтобы сравнение было хоть сколько-то осмысленным. */
const val MIN_DAYS_PER_GROUP = 5

private fun DayFactors.has(factor: DayFactor): Boolean = when (factor) {
    DayFactor.HIGH_STRESS -> stress >= 2
    DayFactor.MODERATE_STRESS -> stress == 1
    DayFactor.CAFFEINE -> caffeine >= 3
    DayFactor.ALCOHOL -> alcohol
    DayFactor.LOW_WATER -> lowWater
    DayFactor.SKIPPED_MEAL -> skippedMeal
}

/**
 * Для каждого фактора сравнивает долю дней с болью среди заполненных дней «с фактором» и «без него».
 * Возвращает только факторы, у которых в обеих группах достаточно дней, по убыванию разницы.
 */
fun computeFactorResults(factors: List<DayFactors>, painDays: Set<Long>): List<FactorResult> =
    DayFactor.entries.mapNotNull { factor ->
        val (with, without) = factors.partition { it.has(factor) }
        if (with.size < MIN_DAYS_PER_GROUP || without.size < MIN_DAYS_PER_GROUP) {
            null
        } else {
            FactorResult(
                factor,
                daysWith = with.size,
                painDaysWith = with.count { it.day in painDays },
                daysWithout = without.size,
                painDaysWithout = without.count { it.day in painDays },
            )
        }
    }.sortedByDescending { it.shareWith - it.shareWithout }

fun computeStats(list: List<HeadacheEntry>): PeriodStats {
    fun daysWhere(predicate: (HeadacheEntry) -> Boolean) =
        list.filter(predicate).map { it.startTime.toLocalDate() }.distinct().size

    fun keyCounts(selector: (HeadacheEntry) -> String): Map<String, Int> =
        list.flatMap { selector(it).toKeySet() }.groupingBy { it }.eachCount()

    val triggerCounts = keyCounts { it.triggers }
    val symptomCounts = keyCounts { it.symptoms }

    return PeriodStats(
        attacks = list.size,
        painDays = daysWhere { true },
        migraineDays = daysWhere { it.type == HeadacheType.MIGRAINE.name },
        tensionDays = daysWhere { it.type == HeadacheType.TENSION.name },
        medDays = daysWhere { it.medication.isNotBlank() },
        avgIntensity = list.mapNotNull { it.intensity }.takeIf { it.isNotEmpty() }?.average(),
        avgDurationMs = list.mapNotNull { e -> e.endTime?.let { it - e.startTime } }
            .takeIf { it.isNotEmpty() }?.average()?.toLong(),
        topTriggers = Trigger.entries
            .mapNotNull { t -> triggerCounts[t.name]?.let { t to it } }
            .sortedByDescending { it.second }
            .take(5),
        topSymptoms = Symptom.entries
            .mapNotNull { s -> symptomCounts[s.name]?.let { s to it } }
            .sortedByDescending { it.second }
            .take(5),
    )
}
