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
