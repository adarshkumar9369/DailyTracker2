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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import com.dailytracker.app.ui.components.TaskCardItem
import com.dailytracker.app.ui.viewmodel.TaskFilterTab

@Composable
fun TasksScreen(
    tasks: List<TaskWithInstanceStatus>,
    selectedFilter: TaskFilterTab,
    use24Hour: Boolean,
    onSelectFilter: (TaskFilterTab) -> Unit,
    onAddTaskClick: () -> Unit,
    onToggleComplete: (TaskWithInstanceStatus) -> Unit,
    onToggleAlarm: (Long, Boolean) -> Unit,
    onSnoozeTask: (Long, Int) -> Unit,
    onEditTask: (TaskWithInstanceStatus) -> Unit,
    onDeleteTask: (Long) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTaskClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Task")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Task Management",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Create, edit, toggle alarms, or filter by status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskFilterTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedFilter == tab,
                        onClick = { onSelectFilter(tab) },
                        label = { Text(tab.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (tasks.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
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
                        Text(
                            text = "No ${selectedFilter.label.lowercase()} tasks found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap the + button to schedule a new one-time, daily, or weekday task.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = tasks,
                        key = { it.taskId }
                    ) { task ->
                        TaskCardItem(
                            task = task,
                            use24Hour = use24Hour,
                            onToggleComplete = { onToggleComplete(task) },
                            onToggleAlarm = { enabled -> onToggleAlarm(task.taskId, enabled) },
                            onSnooze = { mins -> onSnoozeTask(task.taskId, mins) },
                            onEdit = { onEditTask(task) },
                            onDelete = { onDeleteTask(task.taskId) }
                        )
                    }
                }
            }
        }
    }
}
