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
import com.headachediary.app.health.HealthService
import com.headachediary.app.settings.localized
import com.headachediary.app.ui.formatDateTime
import com.headachediary.app.ui.formatDuration
import com.headachediary.app.ui.formatTime
import com.headachediary.app.ui.isOngoing
import com.headachediary.app.ui.toLocalDate
import com.headachediary.app.weather.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Результат нажатия на виджет: текст для тоста и id новой записи (если начат новый приступ). */
data class ToggleResult(val message: String, val startedId: Long?)

object PainWidget {
    /** Записывает начало боли или, если приступ идёт, его окончание. */
    suspend fun toggle(context: Context): ToggleResult {
        val ctx = context.localized()
        val dao = AppDatabase.get(context).dao()
        val latest = dao.latest()
        val now = System.currentTimeMillis()
        val result = if (latest != null && latest.isOngoing(now)) {
            dao.update(latest.copy(endTime = now))
            ToggleResult(ctx.getString(R.string.toast_ended, formatDuration(ctx, now - latest.startTime)), null)
        } else {
            // День с записью о боли не может оставаться отмеченным как «без боли».
            dao.unmarkPainFree(now.toLocalDate().toEpochDay())
            val id = dao.insert(HeadacheEntry(startTime = now))
            ToggleResult(ctx.getString(R.string.toast_started, formatTime(now)), id)
        }
        updateAll(context)
        return result
    }

    /** Перерисовывает все виджеты приложения (кнопку боли и недельный). */
    suspend fun updateAll(context: Context) {
        updatePainWidget(context)
        WeekWidget.update(context)
    }

    private suspend fun updatePainWidget(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PainWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val ctx = context.localized()
        val latest = AppDatabase.get(context).dao().latest()
        val ongoing = latest?.isOngoing(System.currentTimeMillis()) == true

        val views = RemoteViews(context.packageName, R.layout.widget_pain)
        views.setTextViewText(R.id.widget_title, ctx.getString(R.string.app_name))
        if (ongoing && latest != null) {
            views.setTextViewText(R.id.widget_status, ctx.getString(R.string.widget_status_ongoing, formatTime(latest.startTime)))
            views.setTextViewText(R.id.widget_button, ctx.getString(R.string.hero_end))
            views.setInt(R.id.widget_button, "setBackgroundResource", R.drawable.widget_button_end)
        } else {
            views.setTextViewText(
                R.id.widget_status,
                latest?.let { ctx.getString(R.string.widget_status_last, formatDateTime(ctx, it.startTime)) }
                    ?: ctx.getString(R.string.widget_status_hint),
            )
            views.setTextViewText(R.id.widget_button, ctx.getString(R.string.hero_pain_title))
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

private const val WEATHER_TIMEOUT_MS = 9_000L
private const val HEALTH_TIMEOUT_MS = 4_000L

/** Не экспортируется: срабатывает только от нажатия на кнопку виджета этого приложения. */
class PainToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = PainWidget.toggle(appContext)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, result.message, Toast.LENGTH_SHORT).show()
                }
                // Время уже записано и показано; погода дописывается после, в пределах времени, отпущенного приёмнику.
                result.startedId?.let { id ->
                    withTimeoutOrNull(WEATHER_TIMEOUT_MS) { WeatherService.attach(appContext, id) }
                    withTimeoutOrNull(HEALTH_TIMEOUT_MS) { HealthService.attach(appContext, id) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
