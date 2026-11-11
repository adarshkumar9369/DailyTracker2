package com.dailytracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailytracker.app.alarm.ActiveAlarmState
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import com.dailytracker.app.ui.components.ActiveAlarmBanner
import com.dailytracker.app.ui.components.TaskCardItem
import com.dailytracker.app.ui.theme.StatusCompletedGreen
import com.dailytracker.app.ui.theme.StatusMissedRed
import com.dailytracker.app.ui.theme.StatusPendingAmber
import com.dailytracker.app.ui.viewmodel.DashboardSummary
import com.dailytracker.app.util.TaskScheduleCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    dashboard: DashboardSummary,
    use24Hour: Boolean,
    activeRingingAlarm: ActiveAlarmState?,
    onQuickAddTask: () -> Unit,
    onToggleComplete: (TaskWithInstanceStatus) -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onSnoozeTask: (Long, Int) -> Unit,
    onEditTask: (TaskWithInstanceStatus) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onCompleteRingingAlarm: (Long, String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickAddTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = "Quick Add Task") },
                text = { Text("Quick Add Task") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = dashboard.dayOfWeekFormatted.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dashboard.dateFormatted,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (activeRingingAlarm != null) {
                item {
                    ActiveAlarmBanner(
                        activeAlarm = activeRingingAlarm,
                        onComplete = {
                            onCompleteRingingAlarm(
                                activeRingingAlarm.taskId,
                                activeRingingAlarm.cycleDateStr
                            )
                        },
                        onSnooze = { mins ->
                            onSnoozeTask(activeRingingAlarm.taskId, mins)
                        }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Daily Completion",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${dashboard.completionPercentage}%",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${dashboard.completedTasksToday} of ${dashboard.totalTasksToday} completed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = {
                                if (dashboard.totalTasksToday > 0) {
                                    dashboard.completedTasksToday.toFloat() / dashboard.totalTasksToday.toFloat()
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricMiniCard(
                                label = "Total",
                                count = dashboard.totalTasksToday,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniCard(
                                label = "Completed",
                                count = dashboard.completedTasksToday,
                                valueColor = StatusCompletedGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniCard(
                                label = "Pending",
                                count = dashboard.pendingTasksToday,
                                valueColor = StatusPendingAmber,
                                modifier = Modifier.weight(1f)
                            )
                            MetricMiniCard(
                                label = "Missed",
                                count = dashboard.missedTasksToday,
                                valueColor = StatusMissedRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Alarm,
                            contentDescription = "Next Alarm",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPCOMING TASK & NEXT ALARM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val nextTask = dashboard.upcomingTask
                            if (nextTask != null) {
                                Text(
                                    text = nextTask.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val nextTriggerText = nextTask.nextTriggerTimestamp?.let { ts ->
                                    val zdt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
                                    val timeFormatted = TaskScheduleCalculator.formatTime(
                                        zdt.hour,
                                        zdt.minute,
                                        use24Hour
                                    )
                                    val dateFormatted = zdt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    "Scheduled for $timeFormatted ($dateFormatted)"
                                } ?: TaskScheduleCalculator.formatTime(
                                    nextTask.alarmHour,
                                    nextTask.alarmMinute,
                                    use24Hour
                                )
                                Text(
                                    text = nextTriggerText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "No pending alarms scheduled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${dashboard.todayInstances.size} tasks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (dashboard.todayInstances.isEmpty()) {
                item {
                    EmptyTodayPlaceholder(onQuickAddTask = onQuickAddTask)
                }
            } else {
                items(
                    items = dashboard.todayInstances,
                    key = { "${it.taskId}_${it.scheduledDateStr}" }
                ) { taskInstance ->
                    TaskCardItem(
                        task = taskInstance,
                        use24Hour = use24Hour,
                        onToggleComplete = { onToggleComplete(taskInstance) },
                        onToggleAlarm = { enabled -> onToggleAlarm(taskInstance.taskId, enabled) },
                        onSnooze = { mins -> onSnoozeTask(taskInstance.taskId, mins) },
                        onEdit = { onEditTask(taskInstance) },
                        onDelete = { onDeleteTask(taskInstance.taskId) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun MetricMiniCard(
    label: String,
    count: Int,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyTodayPlaceholder(onQuickAddTask: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No tasks scheduled for today",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create your own daily recurring or one-time tasks with persistent repeating alarms.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onQuickAddTask) {
                Text("Add First Task")
            }
        }
    }
}
