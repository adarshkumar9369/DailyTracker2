package com.dailytracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.SnoozeStatus
import com.dailytracker.app.data.model.TaskCompletionStatus

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["scheduledDate"]),
        Index(value = ["recurrenceType"]),
        Index(value = ["alarmEnabled"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val scheduledDate: String,
    val alarmHour: Int,
    val alarmMinute: Int,
    val recurrenceType: RecurrenceType = RecurrenceType.ONE_TIME,
    val selectedWeekdays: String = "",
    val completionStatus: TaskCompletionStatus = TaskCompletionStatus.PENDING,
    val completionTimestamp: Long? = null,
    val lastCompletedDate: String? = null,
    val alarmEnabled: Boolean = true,
    val snoozeStatus: SnoozeStatus = SnoozeStatus.NONE,
    val snoozedUntilTimestamp: Long? = null,
    val nextTriggerTimestamp: Long? = null,
    val activeCycleDate: String? = null,
    val repeatCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
