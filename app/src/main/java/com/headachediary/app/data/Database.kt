package com.headachediary.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY startTime DESC")
    fun all(): Flow<List<HeadacheEntry>>

    @Query("SELECT * FROM entries ORDER BY startTime DESC")
    suspend fun allOnce(): List<HeadacheEntry>

    @Query("SELECT * FROM entries ORDER BY startTime DESC LIMIT 1")
    suspend fun latest(): HeadacheEntry?

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun byId(id: Long): HeadacheEntry?

    @Query("SELECT COUNT(*) FROM entries WHERE startTime >= :from AND startTime < :to")
    suspend fun countBetween(from: Long, to: Long): Int

    @Insert
    suspend fun insert(entry: HeadacheEntry): Long

    @Insert
    suspend fun insertAll(entries: List<HeadacheEntry>)

    @Update
    suspend fun update(entry: HeadacheEntry)

    /** Обновляет только погодные поля, чтобы не затереть правки пользователя в остальных. */
    @Query(
        "UPDATE entries SET temperature = :temperature, pressure = :pressure, " +
            "pressureChange3h = :pressureChange3h, pressureChange24h = :pressureChange24h, " +
            "humidity = :humidity, weatherCode = :weatherCode WHERE id = :id",
    )
    suspend fun updateWeather(
        id: Long,
        temperature: Double?,
        pressure: Double?,
        pressureChange3h: Double?,
        pressureChange24h: Double?,
        humidity: Int?,
        weatherCode: Int?,
    )

    @Delete
    suspend fun delete(entry: HeadacheEntry)

    // Дни, отмеченные как «без боли»

    @Query("SELECT * FROM pain_free_days")
    fun painFreeDays(): Flow<List<PainFreeDay>>

    @Query("SELECT * FROM pain_free_days")
    suspend fun painFreeDaysOnce(): List<PainFreeDay>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markPainFree(day: PainFreeDay)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markPainFreeAll(days: List<PainFreeDay>)

    @Query("DELETE FROM pain_free_days WHERE day = :day")
    suspend fun unmarkPainFree(day: Long)
}

@Database(entities = [HeadacheEntry::class, PainFreeDay::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Версия 2: добавлены погодные поля. Существующие записи остаются без погоды. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "temperature REAL",
                    "pressure REAL",
                    "pressureChange3h REAL",
                    "pressureChange24h REAL",
                    "humidity INTEGER",
                    "weatherCode INTEGER",
                ).forEach { column -> db.execSQL("ALTER TABLE entries ADD COLUMN $column") }
            }
        }

        /** Версия 3: таблица дней, отмеченных как «без боли». */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pain_free_days` (`day` INTEGER NOT NULL, PRIMARY KEY(`day`))")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "headache.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
