package com.headachediary.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.headachediary.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Ежедневная резервная копия в папку, которую выбрал пользователь (это может быть папка, которую синхронизирует
 * облачный диск). Хранится несколько последних файлов, старые удаляются.
 */
object AutoBackup {
    private const val TAG = "HeadacheAutoBackup"
    private const val PREFIX = "headache-diary-"
    private const val KEEP_FILES = 7

    /** Делает копию сейчас. Результат (время и успех) запоминается для показа в настройках. */
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        val folder = AppSettings.autoBackupFolder(context) ?: return@withContext false
        val ok = runCatching { write(context, Uri.parse(folder)) }
            .onFailure { Log.w(TAG, "Auto backup failed", it) }
            .isSuccess
        AppSettings.setAutoBackupResult(context, System.currentTimeMillis(), ok)
        ok
    }

    private suspend fun write(context: Context, treeUri: Uri) {
        val dir = DocumentFile.fromTreeUri(context, treeUri)
        check(dir != null && dir.canWrite()) { "Backup folder is not writable" }

        val dao = AppDatabase.get(context).dao()
        val json = Backup.toJson(
            dao.allOnce(),
            dao.painFreeDaysOnce().map { it.day },
            dao.dayFactorsOnce(),
        )

        // Копия за сегодня заменяет предыдущую за тот же день; имя без расширения: его добавит система по типу файла.
        val baseName = PREFIX + LocalDate.now()
        dir.findFile("$baseName.json")?.delete()
        val file = dir.createFile("application/json", baseName) ?: error("Cannot create the backup file")
        val stream = context.contentResolver.openOutputStream(file.uri, "wt") ?: error("Cannot open the backup file")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }

        // Оставляем только несколько последних копий (имена начинаются с даты, поэтому сортировка по имени = по времени).
        dir.listFiles()
            .filter { f -> f.name?.let { it.startsWith(PREFIX) && it.endsWith(".json") } == true }
            .sortedByDescending { it.name }
            .drop(KEEP_FILES)
            .forEach { it.delete() }
    }

    /** Название выбранной папки для показа в настройках. */
    fun folderName(context: Context): String? {
        val folder = AppSettings.autoBackupFolder(context) ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(folder))?.name
    }
}
