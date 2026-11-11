package com.dailytracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dailytracker.app.data.local.dao.TaskDao
import com.dailytracker.app.data.local.entity.TaskEntity
import com.dailytracker.app.data.local.entity.TaskHistoryEntity

@Database(
    entities = [TaskEntity::class, TaskHistoryEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DailyTrackerDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: DailyTrackerDatabase? = null

        fun getInstance(context: Context): DailyTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailyTrackerDatabase::class.java,
                    "daily_tracker.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
