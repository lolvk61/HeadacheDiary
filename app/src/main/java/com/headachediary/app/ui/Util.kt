package com.headachediary.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.data.HeadacheType
import com.headachediary.app.data.MedHelp
import com.headachediary.app.data.symptomLabels
import com.headachediary.app.data.triggerLabels
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ruLocale: Locale = Locale.forLanguageTag("ru")
const val DAY_MS = 24L * 60 * 60 * 1000

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ruLocale)
private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ruLocale)
private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", ruLocale)

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()

fun formatTime(ms: Long): String = ms.toLocalDateTime().format(timeFmt)
fun formatDateTime(ms: Long): String = ms.toLocalDateTime().format(dateTimeFmt)
fun formatDayHeader(date: LocalDate): String =
    date.format(dayHeaderFmt).replaceFirstChar { it.uppercase() }

fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "$m мин"
        m == 0L -> "$h ч"
        else -> "$h ч $m мин"
    }
}

/** Приступ считается идущим, если он не закрыт и начался не более суток назад. */
fun HeadacheEntry.isOngoing(now: Long): Boolean = endTime == null && now - startTime < DAY_MS

fun timeRange(e: HeadacheEntry): String {
    val end = e.endTime ?: return "с ${formatTime(e.startTime)}"
    return "${formatTime(e.startTime)} – ${formatTime(end)} (${formatDuration(end - e.startTime)})"
}

/** Системные диалоги выбора даты, затем времени. */
fun pickDateTime(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val dt = initial.toLocalDateTime()
    DatePickerDialog(
        context,
        { _, y, m, d ->
            TimePickerDialog(
                context,
                { _, h, min -> onPicked(LocalDateTime.of(y, m + 1, d, h, min).toMillis()) },
                dt.hour,
                dt.minute,
                true,
            ).show()
        },
        dt.year,
        dt.monthValue - 1,
        dt.dayOfMonth,
    ).show()
}

/** Текст для отправки врачу (CSV, разделитель ";"). */
fun buildCsv(entries: List<HeadacheEntry>): String {
    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    val sb = StringBuilder("Начало;Конец;Тип;Сила (1-10);Симптомы;Провокаторы;Лекарство;Помогло;Заметки\n")
    entries.sortedBy { it.startTime }.forEach { e ->
        val help = MedHelp.entries.firstOrNull { it.name == e.medicationHelped }?.label.orEmpty()
        sb.append(
            listOf(
                formatDateTime(e.startTime),
                e.endTime?.let { formatDateTime(it) }.orEmpty(),
                HeadacheType.fromKey(e.type).label,
                e.intensity?.toString().orEmpty(),
                esc(e.symptoms.symptomLabels().joinToString(", ")),
                esc(e.triggers.triggerLabels().joinToString(", ")),
                esc(e.medication),
                help,
                esc(e.notes),
            ).joinToString(";"),
        ).append('\n')
    }
    return sb.toString()
}
