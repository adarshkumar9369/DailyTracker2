package com.dailytracker.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailytracker.app.alarm.ActiveAlarmState
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.SnoozeStatus
import com.dailytracker.app.data.model.TaskCompletionStatus
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import com.dailytracker.app.ui.theme.StatusCompletedBg
import com.dailytracker.app.ui.theme.StatusCompletedGreen
import com.dailytracker.app.ui.theme.StatusMissedBg
import com.dailytracker.app.ui.theme.StatusMissedRed
import com.dailytracker.app.ui.theme.StatusPendingAmber
import com.dailytracker.app.ui.theme.StatusPendingBg
import com.dailytracker.app.util.TaskScheduleCalculator

@Composable
fun ActiveAlarmBanner(
    activeAlarm: ActiveAlarmState,
    onComplete: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StatusMissedBg
        ),
        border = BorderStroke(1.5.dp, StatusMissedRed)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsActive,
                    contentDescription = "Ringing Alarm",
                    tint = StatusMissedRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ALARM RINGING • REPEAT #${activeAlarm.repeatCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = StatusMissedRed,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = activeAlarm.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (activeAlarm.description.isNotBlank()) {
                Text(
                    text = activeAlarm.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCompletedGreen)
                ) {
                    Text("Complete Now")
                }
                OutlinedButton(onClick = { onSnooze(5) }) { Text("5m") }
                OutlinedButton(onClick = { onSnooze(10) }) { Text("10m") }
                OutlinedButton(onClick = { onSnooze(15) }) { Text("15m") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCardItem(
    task: TaskWithInstanceStatus,
    use24Hour: Boolean,
    onToggleComplete: () -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onSnooze: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showEditDeleteActions: Boolean = true
) {
    val isCompleted = task.status == TaskCompletionStatus.COMPLETED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isCompleted) "Mark Pending" else "Mark Completed",
                        tint = if (isCompleted) StatusCompletedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPill(
                            text = TaskScheduleCalculator.formatTime(task.alarmHour, task.alarmMinute, use24Hour),
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )

                        val (statusBg, statusFg) = when (task.status) {
                            TaskCompletionStatus.COMPLETED -> StatusCompletedBg to StatusCompletedGreen
                            TaskCompletionStatus.MISSED -> StatusMissedBg to StatusMissedRed
                            TaskCompletionStatus.PENDING -> StatusPendingBg to StatusPendingAmber
                        }
                        StatusPill(
                            text = task.status.displayName,
                            backgroundColor = statusBg,
                            contentColor = statusFg
                        )

                        val recurrenceLabel = when (task.recurrenceType) {
                            RecurrenceType.ONE_TIME -> task.baseScheduledDateStr
                            RecurrenceType.DAILY -> "Daily"
                            RecurrenceType.SPECIFIC_DAYS -> formatWeekdaysShort(task.selectedWeekdays)
                        }
                        StatusPill(
                            text = recurrenceLabel,
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!isCompleted && task.snoozeStatus != SnoozeStatus.NONE) {
                            StatusPill(
                                text = if (task.snoozeStatus == SnoozeStatus.SNOOZED) "Snoozed" else "Repeating (#${task.repeatCount})",
                                backgroundColor = StatusMissedBg,
                                contentColor = StatusMissedRed
                            )
                        }
                    }
                }

                if (showEditDeleteActions) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Switch(
                            checked = task.alarmEnabled,
                            onCheckedChange = onToggleAlarm
                        )
                    }
                }
            }

            if (showEditDeleteActions) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (task.alarmEnabled) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (task.alarmEnabled) "Persistent alarm ON" else "Alarm OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isCompleted && task.alarmEnabled) {
                            Text(
                                text = "Snooze 10m",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onSnooze(10) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit Task",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete Task",
                                tint = StatusMissedRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatWeekdaysShort(days: Set<Int>): String {
    if (days.size == 7) return "Every Day"
    val map = mapOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )
    return days.sorted().mapNotNull { map[it] }.joinToString(", ")
}
