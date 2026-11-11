package com.dailytracker.app.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dailytracker.app.DailyTrackerApp
import com.dailytracker.app.alarm.AlarmService
import com.dailytracker.app.ui.theme.DailyTrackerTheme
import com.dailytracker.app.ui.theme.StatusCompletedGreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESC = "extra_task_desc"
        const val EXTRA_CYCLE_DATE = "extra_cycle_date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenFlags()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Require explicit Complete or Snooze action
            }
        })

        val fallbackTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val fallbackTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Scheduled Task"
        val fallbackDesc = intent.getStringExtra(EXTRA_TASK_DESC) ?: ""
        val fallbackCycleDate = intent.getStringExtra(EXTRA_CYCLE_DATE)
            ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val app = application as DailyTrackerApp

        setContent {
            val activeAlarm by AlarmService.activeAlarmFlow.collectAsState()
            val settings by app.container.settingsManager.settingsFlow.collectAsState(
                initial = com.dailytracker.app.data.model.AppSettings()
            )

            val taskId = activeAlarm?.taskId ?: fallbackTaskId
            val title = activeAlarm?.title ?: fallbackTitle
            val description = activeAlarm?.description ?: fallbackDesc
            val cycleDate = activeAlarm?.cycleDateStr ?: fallbackCycleDate
            val repeatCount = activeAlarm?.repeatCount ?: 1

            DailyTrackerTheme(themeMode = settings.themeMode) {
                FullScreenAlarmContent(
                    title = title,
                    description = description,
                    repeatCount = repeatCount,
                    repeatIntervalMinutes = settings.repeatIntervalMinutes,
                    use24Hour = settings.use24HourFormat,
                    onCompleteTask = {
                        lifecycleScope.launch {
                            if (taskId > 0L) {
                                app.container.taskRepository.markTaskCompleted(
                                    taskId = taskId,
                                    targetDateStr = cycleDate
                                )
                            } else {
                                AlarmService.stopAlarmIfMatching(applicationContext)
                            }
                            finish()
                        }
                    },
                    onSnoozeTask = { snoozeMins ->
                        lifecycleScope.launch {
                            if (taskId > 0L) {
                                app.container.taskRepository.snoozeTaskAlarm(
                                    taskId = taskId,
                                    snoozeMinutes = snoozeMins,
                                    cycleDateStr = cycleDate
                                )
                            } else {
                                AlarmService.stopAlarmIfMatching(applicationContext)
                            }
                            finish()
                        }
                    }
                )
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun FullScreenAlarmContent(
    title: String,
    description: String,
    repeatCount: Int,
    repeatIntervalMinutes: Int,
    use24Hour: Boolean,
    onCompleteTask: () -> Unit,
    onSnoozeTask: (Int) -> Unit
) {
    val currentTimeText = LocalTime.now().format(
        DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "hh:mm a", Locale.US)
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Alarm,
                    contentDescription = "Persistent Task Alarm",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentTimeText,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PERSISTENT TASK ALARM • CYCLE #$repeatCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This alarm will repeat every $repeatIntervalMinutes minutes until you mark the task as Completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCompleteTask,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCompletedGreen)
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(
                        text = "Mark Task Completed (Stop Alarm)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Or Snooze Temporarily:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(5, 10, 15).forEach { mins ->
                        OutlinedButton(
                            onClick = { onSnoozeTask(mins) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("${mins}m")
                        }
                    }
                }
            }
        }
    }
}
