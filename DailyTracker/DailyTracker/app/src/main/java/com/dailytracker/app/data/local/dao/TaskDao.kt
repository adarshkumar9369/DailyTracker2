package com.dailytracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailytracker.app.data.local.entity.TaskEntity
import com.dailytracker.app.data.local.entity.TaskHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY alarmHour ASC, alarmMinute ASC, id ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY alarmHour ASC, alarmMinute ASC, id ASC")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    fun observeTaskById(taskId: Long): Flow<TaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    @Query("SELECT * FROM task_history ORDER BY dateStr DESC, scheduledHour ASC, scheduledMinute ASC")
    fun observeAllHistory(): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history WHERE dateStr = :dateStr ORDER BY scheduledHour ASC, scheduledMinute ASC")
    fun observeHistoryForDate(dateStr: String): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history WHERE dateStr BETWEEN :startDateStr AND :endDateStr ORDER BY dateStr DESC, scheduledHour ASC")
    fun observeHistoryBetweenDates(startDateStr: String, endDateStr: String): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history WHERE dateStr = :dateStr")
    suspend fun getHistoryForDateOnce(dateStr: String): List<TaskHistoryEntity>

    @Query("SELECT * FROM task_history WHERE taskId = :taskId AND dateStr = :dateStr LIMIT 1")
    suspend fun getHistoryEntry(taskId: Long, dateStr: String): TaskHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistoryEntry(entry: TaskHistoryEntity): Long

    @Query("DELETE FROM task_history WHERE taskId = :taskId AND dateStr = :dateStr")
    suspend fun deleteHistoryEntry(taskId: Long, dateStr: String)
}
