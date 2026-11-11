package com.dailytracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dailytracker.app.data.model.TaskCompletionStatus

@Entity(
    tableName = "task_history",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId", "dateStr"], unique = true),
        Index(value = ["dateStr"]),
        Index(value = ["status"])
    ]
)
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long = 0L,
    val taskId: Long,
    val taskTitleSnapshot: String,
    val dateStr: String,
    val scheduledHour: Int,
    val scheduledMinute: Int,
    val status: TaskCompletionStatus,
    val completedAtTimestamp: Long? = null
)
