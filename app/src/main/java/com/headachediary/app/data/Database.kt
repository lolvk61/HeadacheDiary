package com.headachediary.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY startTime DESC")
    fun all(): Flow<List<HeadacheEntry>>

    @Query("SELECT * FROM entries ORDER BY startTime DESC LIMIT 1")
    suspend fun latest(): HeadacheEntry?

    @Insert
    suspend fun insert(entry: HeadacheEntry): Long

    @Update
    suspend fun update(entry: HeadacheEntry)

    @Delete
    suspend fun delete(entry: HeadacheEntry)
}

@Database(entities = [HeadacheEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): EntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "headache.db",
            ).build().also { instance = it }
        }
    }
}
