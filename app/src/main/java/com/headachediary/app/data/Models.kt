package com.headachediary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Одна запись о приступе боли. startTime ставится автоматически в момент нажатия кнопки.
 * Списки (симптомы, провокаторы) хранятся строкой с именами enum через запятую.
 */
@Entity(tableName = "entries")
data class HeadacheEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val type: String = HeadacheType.UNKNOWN.name,
    val intensity: Int? = null,
    val symptoms: String = "",
    val triggers: String = "",
    val medication: String = "",
    val medicationHelped: String = "",
    val notes: String = "",
)

enum class HeadacheType(val label: String) {
    MIGRAINE("Мигрень"),
    TENSION("Обычная головная боль"),
    UNKNOWN("Тип не указан");

    companion object {
        fun fromKey(key: String): HeadacheType = entries.firstOrNull { it.name == key } ?: UNKNOWN
    }
}

enum class SymptomGroup(val title: String) {
    MIGRAINE("Чаще при мигрени"),
    TENSION("Чаще при обычной головной боли"),
    COMMON("Другое"),
}

enum class Symptom(val label: String, val group: SymptomGroup) {
    PULSATING("Пульсирующая боль", SymptomGroup.MIGRAINE),
    ONE_SIDED("Боль с одной стороны", SymptomGroup.MIGRAINE),
    NAUSEA("Тошнота", SymptomGroup.MIGRAINE),
    VOMITING("Рвота", SymptomGroup.MIGRAINE),
    PHOTOPHOBIA("Боязнь света", SymptomGroup.MIGRAINE),
    PHONOPHOBIA("Боязнь звуков", SymptomGroup.MIGRAINE),
    SMELL_SENSITIVITY("Чувствительность к запахам", SymptomGroup.MIGRAINE),
    AURA("Аура (мерцание, пятна, онемение)", SymptomGroup.MIGRAINE),
    WORSE_MOVEMENT("Усиливается от движения", SymptomGroup.MIGRAINE),

    PRESSING("Давящая, «обруч» на голове", SymptomGroup.TENSION),
    BOTH_SIDES("Боль с обеих сторон", SymptomGroup.TENSION),
    MILD("Лёгкая или умеренная", SymptomGroup.TENSION),
    NECK_TENSION("Напряжение шеи и плеч", SymptomGroup.TENSION),
    NOT_WORSE_MOVEMENT("Не усиливается от движения", SymptomGroup.TENSION),

    DIZZINESS("Головокружение", SymptomGroup.COMMON),
    FATIGUE("Усталость, разбитость", SymptomGroup.COMMON),
    IRRITABILITY("Раздражительность", SymptomGroup.COMMON),
    CONCENTRATION("Трудно сосредоточиться", SymptomGroup.COMMON),
    EYE_PAIN("Боль за глазом", SymptomGroup.COMMON),
}

enum class Trigger(val label: String) {
    SLEEP("Недосып или пересып"),
    STRESS("Стресс"),
    ALCOHOL("Алкоголь"),
    CAFFEINE("Кофеин"),
    SKIPPED_MEAL("Пропуск еды"),
    DEHYDRATION("Мало воды"),
    WEATHER("Погода"),
    MENSTRUATION("Менструация"),
    BRIGHT_LIGHT("Яркий свет"),
    SCREEN("Долго за экраном"),
    SMELLS("Резкие запахи"),
    FOOD("Определённая еда"),
    PHYSICAL("Физическая нагрузка"),
}

enum class MedHelp(val label: String) {
    YES("Помогло"),
    PARTLY("Частично"),
    NO("Не помогло"),
}

fun String.toKeySet(): Set<String> = split(",").filter { it.isNotBlank() }.toSet()

fun Set<String>.toggle(key: String): Set<String> = if (key in this) this - key else this + key

fun String.symptomLabels(): List<String> =
    toKeySet().mapNotNull { k -> Symptom.entries.firstOrNull { it.name == k }?.label }

fun String.triggerLabels(): List<String> =
    toKeySet().mapNotNull { k -> Trigger.entries.firstOrNull { it.name == k }?.label }
