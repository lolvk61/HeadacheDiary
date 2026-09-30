package com.headachediary.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import com.headachediary.app.R
import com.headachediary.app.data.HeadacheEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DAY_MS = 24L * 60 * 60 * 1000

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

/** Язык, выбранный в приложении (или системный): берётся из конфигурации контекста. */
fun Context.appLocale(): Locale = resources.configuration.locales[0]

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()

fun formatTime(ms: Long): String = ms.toLocalDateTime().format(timeFmt)

fun formatDateTime(context: Context, ms: Long): String =
    ms.toLocalDateTime().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", context.appLocale()))

fun formatDate(context: Context, date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", context.appLocale()))

fun formatDayHeader(context: Context, date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", context.appLocale()))
        .replaceFirstChar { it.uppercase() }

fun formatDuration(context: Context, ms: Long): String {
    val totalMin = (ms / 60_000).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> context.getString(R.string.duration_min, m)
        m == 0L -> context.getString(R.string.duration_h, h)
        else -> context.getString(R.string.duration_h_min, h, m)
    }
}

/** Приступ считается идущим, если он не закрыт и начался не более суток назад. */
fun HeadacheEntry.isOngoing(now: Long): Boolean = endTime == null && now - startTime < DAY_MS

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
