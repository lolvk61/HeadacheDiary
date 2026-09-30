package com.headachediary.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.headachediary.app.MainActivity
import com.headachediary.app.R
import com.headachediary.app.data.AppDatabase
import com.headachediary.app.data.AutoBackup
import com.headachediary.app.data.PainFreeDay
import com.headachediary.app.data.pressureRelevance
import com.headachediary.app.settings.AppSettings
import com.headachediary.app.settings.localized
import com.headachediary.app.ui.outlookMessage
import com.headachediary.app.weather.WeatherService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Каналы и отправка уведомлений. Тексты берутся на языке, выбранном в приложении. */
object Notifications {
    private const val CHANNEL_REMINDER = "daily_reminder"
    private const val CHANNEL_FORECAST = "pressure_forecast"
    private const val ID_REMINDER = 1001
    private const val ID_FORECAST = 1002

    fun ensureChannels(context: Context) {
        val ctx = context.localized()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, ctx.getString(R.string.ch_reminder), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FORECAST, ctx.getString(R.string.ch_forecast), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Есть ли право показывать уведомления (с Android 13 его нужно запрашивать). */
    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openApp(context: Context, requestCode: Int, newEntry: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (newEntry) putExtra(MainActivity.EXTRA_NEW_ENTRY, true)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun postDailyReminder(context: Context) {
        if (!canPost(context)) return
        val ctx = context.localized()
        val noPain = PendingIntent.getBroadcast(
            context,
            11,
            Intent(context, PainFreeActionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.reminder_title))
            .setContentText(ctx.getString(R.string.reminder_text))
            .setContentIntent(openApp(context, 10, newEntry = false))
            .addAction(0, ctx.getString(R.string.action_no_pain), noPain)
            .addAction(0, ctx.getString(R.string.action_had_pain), openApp(context, 12, newEntry = true))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_REMINDER, notification)
    }

    @SuppressLint("MissingPermission")
    fun postForecast(context: Context, text: String) {
        if (!canPost(context)) return
        val ctx = context.localized()
        val notification = NotificationCompat.Builder(context, CHANNEL_FORECAST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, 13, newEntry = false))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_FORECAST, notification)
    }

    fun cancelReminder(context: Context) = NotificationManagerCompat.from(context).cancel(ID_REMINDER)
}

/**
 * Ежедневные будильники. Используются неточные будильники (setAndAllowWhileIdle): точное время не важно,
 * зато не нужно специальное разрешение. После срабатывания приёмник сам ставит следующий.
 */
object ReminderScheduler {
    private const val REQ_DAILY = 100
    private const val REQ_FORECAST = 101
    private const val REQ_BACKUP = 102
    private const val FORECAST_HOUR = 8
    private const val BACKUP_HOUR = 3

    fun scheduleAll(context: Context) {
        val app = context.applicationContext
        if (AppSettings.reminderEnabled(app)) {
            val minutes = AppSettings.reminderMinutes(app)
            schedule(app, REQ_DAILY, minutes / 60, minutes % 60, DailyReminderReceiver::class.java)
        } else {
            cancel(app, REQ_DAILY, DailyReminderReceiver::class.java)
        }
        if (AppSettings.forecastAlertEnabled(app)) {
            schedule(app, REQ_FORECAST, FORECAST_HOUR, 0, ForecastAlertReceiver::class.java)
        } else {
            cancel(app, REQ_FORECAST, ForecastAlertReceiver::class.java)
        }
        if (AppSettings.autoBackupFolder(app) != null) {
            schedule(app, REQ_BACKUP, BACKUP_HOUR, 0, AutoBackupReceiver::class.java)
        } else {
            cancel(app, REQ_BACKUP, AutoBackupReceiver::class.java)
        }
    }

    private fun pending(context: Context, requestCode: Int, receiver: Class<*>): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun schedule(context: Context, requestCode: Int, hour: Int, minute: Int, receiver: Class<*>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending(context, requestCode, receiver))
    }

    private fun cancel(context: Context, requestCode: Int, receiver: Class<*>) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, requestCode, receiver))
    }
}

/** Вечером спрашивает про боль, но только если за сегодня ещё нет ни записи, ни отметки «без боли». */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.scheduleAll(app)
                if (!AppSettings.reminderEnabled(app)) return@launch
                val dao = AppDatabase.get(app).dao()
                val today = LocalDate.now()
                val zone = ZoneId.systemDefault()
                val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val hasEntries = dao.countBetween(from, to) > 0
                val marked = dao.painFreeDaysOnce().any { it.day == today.toEpochDay() }
                if (!hasEntries && !marked) Notifications.postDailyReminder(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Кнопка «Боли не было» в уведомлении: отмечает сегодняшний день. */
class PainFreeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.get(app).dao().markPainFree(PainFreeDay(LocalDate.now().toEpochDay()))
                Notifications.cancelReminder(app)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, app.localized().getString(R.string.toast_pain_free_marked), Toast.LENGTH_SHORT).show()
                }
                com.headachediary.app.widget.PainWidget.updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Утром смотрит прогноз и предупреждает, если ожидается заметный перепад давления. Если по записям видно,
 * что перепады почти не предшествуют приступам, уведомление не отправляется — чтобы не надоедать.
 */
class ForecastAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.scheduleAll(app)
                if (!AppSettings.forecastAlertEnabled(app) || !AppSettings.weatherEnabled(app)) return@launch
                val outlook = WeatherService.pressureOutlook(app) ?: return@launch
                if (!outlook.isSharp) return@launch
                val relevance = pressureRelevance(AppDatabase.get(app).dao().allOnce())
                if (relevance.unlikely) return@launch
                val ctx = app.localized()
                Notifications.postForecast(app, outlookMessage(ctx, outlook, relevance))
            } finally {
                pending.finish()
            }
        }
    }
}

/** Ночью делает автоматическую резервную копию в выбранную папку. */
class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.scheduleAll(app)
                AutoBackup.run(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/** После перезагрузки телефона будильники сбрасываются — ставим их заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderScheduler.scheduleAll(context)
    }
}
