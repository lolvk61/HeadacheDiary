package com.headachediary.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.headachediary.app.MainActivity
import com.headachediary.app.R
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.HeadacheEntry
import com.headachediary.app.ui.formatDateTime
import com.headachediary.app.ui.formatDuration
import com.headachediary.app.ui.formatTime
import com.headachediary.app.ui.isOngoing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object PainWidget {
    /** Записывает начало боли или, если приступ идёт, его окончание. Возвращает текст для тоста. */
    suspend fun toggle(context: Context): String {
        val dao = AppDatabase.get(context).dao()
        val latest = dao.latest()
        val now = System.currentTimeMillis()
        val message = if (latest != null && latest.isOngoing(now)) {
            dao.update(latest.copy(endTime = now))
            "Боль прошла. Длилась ${formatDuration(now - latest.startTime)}"
        } else {
            dao.insert(HeadacheEntry(startTime = now))
            "Записано: боль с ${formatTime(now)}"
        }
        updateAll(context)
        return message
    }

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PainWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val latest = AppDatabase.get(context).dao().latest()
        val ongoing = latest?.isOngoing(System.currentTimeMillis()) == true

        val views = RemoteViews(context.packageName, R.layout.widget_pain)
        if (ongoing && latest != null) {
            views.setTextViewText(R.id.widget_status, "Приступ с ${formatTime(latest.startTime)}")
            views.setTextViewText(R.id.widget_button, "Боль прошла")
            views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_button_end)
        } else {
            views.setTextViewText(
                R.id.widget_status,
                latest?.let { "Последний раз: ${formatDateTime(it.startTime)}" }
                    ?: "Нажмите, когда начнётся боль",
            )
            views.setTextViewText(R.id.widget_button, "Болит голова")
            views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_button_pain)
        }

        val toggleIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PainToggleReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_button, toggleIntent)
        views.setOnClickPendingIntent(R.id.widget_header, openIntent)

        manager.updateAppWidget(ids, views)
    }
}

class PainWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PainWidget.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Не экспортируется: срабатывает только от нажатия на кнопку виджета этого приложения. */
class PainToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = PainWidget.toggle(appContext)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
