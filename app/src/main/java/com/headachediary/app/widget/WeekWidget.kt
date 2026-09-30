package com.headachediary.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.headachediary.app.MainActivity
import com.headachediary.app.R
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.PainFreeDay
import com.headachediary.app.settings.localized
import com.headachediary.app.ui.appLocale
import com.headachediary.app.ui.toLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle

/** Виджет «Неделя»: последние 7 дней цветными кружками и кнопка «Без боли сегодня». */
object WeekWidget {
    private val letterIds = intArrayOf(
        R.id.week_l0, R.id.week_l1, R.id.week_l2, R.id.week_l3, R.id.week_l4, R.id.week_l5, R.id.week_l6,
    )
    private val dayIds = intArrayOf(
        R.id.week_d0, R.id.week_d1, R.id.week_d2, R.id.week_d3, R.id.week_d4, R.id.week_d5, R.id.week_d6,
    )

    suspend fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, WeekWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val ctx = context.localized()
        val locale = ctx.appLocale()
        val dao = AppDatabase.get(context).dao()
        val byDay = dao.allOnce().groupBy { it.startTime.toLocalDate() }
        val painFree = dao.painFreeDaysOnce().map { it.day }.toSet()
        val today = LocalDate.now()
        val defaultText = ContextCompat.getColor(ctx, R.color.widget_text)

        val views = RemoteViews(context.packageName, R.layout.widget_week)
        views.setTextViewText(R.id.week_title, ctx.getString(R.string.week_title))

        for (i in 0..6) {
            val date = today.minusDays((6 - i).toLong())
            val list = byDay[date].orEmpty()
            val strongest = list.mapNotNull { it.intensity }.maxOrNull()
            val background = when {
                list.isNotEmpty() -> when {
                    strongest == null -> R.drawable.dot_unknown
                    strongest <= 3 -> R.drawable.dot_low
                    strongest <= 6 -> R.drawable.dot_mid
                    else -> R.drawable.dot_high
                }
                date.toEpochDay() in painFree -> R.drawable.dot_free
                else -> R.drawable.dot_none
            }
            val textColor = when (background) {
                R.drawable.dot_none -> defaultText
                R.drawable.dot_low -> 0xFF000000.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            views.setTextViewText(letterIds[i], date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale))
            views.setTextViewText(dayIds[i], date.dayOfMonth.toString())
            views.setInt(dayIds[i], "setBackgroundResource", background)
            views.setTextColor(dayIds[i], textColor)
        }

        val todayHasEntries = byDay[today].orEmpty().isNotEmpty()
        val todayMarked = today.toEpochDay() in painFree
        views.setTextViewText(
            R.id.week_button,
            ctx.getString(
                when {
                    todayHasEntries -> R.string.week_widget_has_entry
                    todayMarked -> R.string.week_widget_unmark
                    else -> R.string.week_widget_mark
                },
            ),
        )
        val toggle = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, PainFreeToggleReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!todayHasEntries) views.setOnClickPendingIntent(R.id.week_button, toggle)
        views.setOnClickPendingIntent(R.id.week_header, open)

        manager.updateAppWidget(ids, views)
    }
}

class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WeekWidget.update(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Не экспортируется: кнопка виджета отмечает или снимает отметку «без боли» за сегодня. */
class PainFreeToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(app).dao()
                val today = LocalDate.now()
                val hasEntries = dao.allOnce().any { it.startTime.toLocalDate() == today }
                if (!hasEntries) {
                    val day = today.toEpochDay()
                    if (dao.painFreeDaysOnce().any { it.day == day }) dao.unmarkPainFree(day) else dao.markPainFree(PainFreeDay(day))
                }
                PainWidget.updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
