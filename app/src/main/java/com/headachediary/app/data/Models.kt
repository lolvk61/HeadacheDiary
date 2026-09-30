package com.headachediary.app.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.headachediary.app.R

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
    // Погода на момент начала приступа (давление в гПа, температура в °C); null — не записана.
    val temperature: Double? = null,
    val pressure: Double? = null,
    val pressureChange3h: Double? = null,
    val pressureChange24h: Double? = null,
    val humidity: Int? = null,
    val weatherCode: Int? = null,
)

/** Перепад давления за 3 часа, начиная с которого считаем его заметным (в гПа; ≈ 2,3 мм рт. ст.). */
const val SHARP_PRESSURE_CHANGE_HPA = 3.0

fun HeadacheEntry.hasSharpPressureChange(): Boolean =
    pressureChange3h?.let { kotlin.math.abs(it) >= SHARP_PRESSURE_CHANGE_HPA } == true

enum class HeadacheType(@StringRes val labelRes: Int) {
    MIGRAINE(R.string.type_migraine),
    TENSION(R.string.type_tension),
    UNKNOWN(R.string.type_unknown);

    companion object {
        fun fromKey(key: String): HeadacheType = entries.firstOrNull { it.name == key } ?: UNKNOWN
    }
}

enum class SymptomGroup(@StringRes val titleRes: Int) {
    MIGRAINE(R.string.group_migraine),
    TENSION(R.string.group_tension),
    COMMON(R.string.group_common),
}

enum class Symptom(@StringRes val labelRes: Int, val group: SymptomGroup) {
    PULSATING(R.string.sym_pulsating, SymptomGroup.MIGRAINE),
    ONE_SIDED(R.string.sym_one_sided, SymptomGroup.MIGRAINE),
    NAUSEA(R.string.sym_nausea, SymptomGroup.MIGRAINE),
    VOMITING(R.string.sym_vomiting, SymptomGroup.MIGRAINE),
    PHOTOPHOBIA(R.string.sym_photophobia, SymptomGroup.MIGRAINE),
    PHONOPHOBIA(R.string.sym_phonophobia, SymptomGroup.MIGRAINE),
    SMELL_SENSITIVITY(R.string.sym_smell, SymptomGroup.MIGRAINE),
    AURA(R.string.sym_aura, SymptomGroup.MIGRAINE),
    WORSE_MOVEMENT(R.string.sym_worse_movement, SymptomGroup.MIGRAINE),

    PRESSING(R.string.sym_pressing, SymptomGroup.TENSION),
    BOTH_SIDES(R.string.sym_both_sides, SymptomGroup.TENSION),
    MILD(R.string.sym_mild, SymptomGroup.TENSION),
    NECK_TENSION(R.string.sym_neck_tension, SymptomGroup.TENSION),
    NOT_WORSE_MOVEMENT(R.string.sym_not_worse_movement, SymptomGroup.TENSION),

    DIZZINESS(R.string.sym_dizziness, SymptomGroup.COMMON),
    FATIGUE(R.string.sym_fatigue, SymptomGroup.COMMON),
    IRRITABILITY(R.string.sym_irritability, SymptomGroup.COMMON),
    CONCENTRATION(R.string.sym_concentration, SymptomGroup.COMMON),
    EYE_PAIN(R.string.sym_eye_pain, SymptomGroup.COMMON),
}

enum class Trigger(@StringRes val labelRes: Int) {
    SLEEP(R.string.trg_sleep),
    STRESS(R.string.trg_stress),
    ALCOHOL(R.string.trg_alcohol),
    CAFFEINE(R.string.trg_caffeine),
    SKIPPED_MEAL(R.string.trg_skipped_meal),
    DEHYDRATION(R.string.trg_dehydration),
    WEATHER(R.string.trg_weather),
    MENSTRUATION(R.string.trg_menstruation),
    BRIGHT_LIGHT(R.string.trg_bright_light),
    SCREEN(R.string.trg_screen),
    SMELLS(R.string.trg_smells),
    FOOD(R.string.trg_food),
    PHYSICAL(R.string.trg_physical),
}

enum class MedHelp(@StringRes val labelRes: Int) {
    YES(R.string.med_yes),
    PARTLY(R.string.med_partly),
    NO(R.string.med_no),
}

fun String.toKeySet(): Set<String> = split(",").filter { it.isNotBlank() }.toSet()

fun Set<String>.toggle(key: String): Set<String> = if (key in this) this - key else this + key

/** Названия симптомов на языке приложения, в порядке объявления. */
fun String.symptomLabels(context: Context): List<String> {
    val keys = toKeySet()
    return Symptom.entries.filter { it.name in keys }.map { context.getString(it.labelRes) }
}

fun String.triggerLabels(context: Context): List<String> {
    val keys = toKeySet()
    return Trigger.entries.filter { it.name in keys }.map { context.getString(it.labelRes) }
}
