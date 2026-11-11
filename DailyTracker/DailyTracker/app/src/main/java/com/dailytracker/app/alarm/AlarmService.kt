package com.dailytracker.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.dailytracker.app.DailyTrackerApp
import com.dailytracker.app.data.model.AlarmToneOption
import com.dailytracker.app.notification.TaskNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveAlarmState(
    val taskId: Long,
    val title: String,
    val description: String,
    val cycleDateStr: String,
    val repeatCount: Int,
    val defaultSnoozeMinutes: Int,
    val repeatIntervalMinutes: Int
)

class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.dailytracker.app.action.START_ALARM"
        const val ACTION_STOP_ALARM = "com.dailytracker.app.action.STOP_ALARM"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_CYCLE_DATE = "extra_cycle_date"

        private val _activeAlarmFlow = MutableStateFlow<ActiveAlarmState?>(null)
        val activeAlarmFlow: StateFlow<ActiveAlarmState?> = _activeAlarmFlow.asStateFlow()

        fun startAlarm(context: Context, taskId: Long, cycleDateStr: String?) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_CYCLE_DATE, cycleDateStr ?: "")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlarmIfMatching(context: Context, taskId: Long? = null) {
            val current = _activeAlarmFlow.value
            if (taskId == null || current == null || current.taskId == taskId) {
                val intent = Intent(context, AlarmService::class.java).apply {
                    action = ACTION_STOP_ALARM
                    if (taskId != null) putExtra(EXTRA_TASK_ID, taskId)
                }
                runCatching { context.startService(intent) }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringTimeoutJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                val targetId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val currentId = _activeAlarmFlow.value?.taskId
                if (targetId == -1L || currentId == null || targetId == currentId) {
                    stopPlaybackAndFinish()
                }
                return START_NOT_STICKY
            }

            ACTION_START_ALARM -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val cycleDate = intent.getStringExtra(EXTRA_CYCLE_DATE)
                if (taskId <= 0L) {
                    stopPlaybackAndFinish()
                    return START_NOT_STICKY
                }
                acquireWakeLock()
                serviceScope.launch {
                    handleStartAlarm(taskId, cycleDate)
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private suspend fun handleStartAlarm(taskId: Long, cycleDate: String?) {
        val app = application as DailyTrackerApp
        val repository = app.container.taskRepository
        val settingsManager = app.container.settingsManager
        val notificationManager = app.container.notificationManager

        val settings = settingsManager.getSettingsOnce()
        val updatedTask = repository.onAlarmTriggeredAndScheduleRepeat(taskId, cycleDate)
        if (updatedTask == null) {
            stopPlaybackAndFinish()
            return
        }

        val activeCycle = updatedTask.activeCycleDate ?: cycleDate ?: ""
        val state = ActiveAlarmState(
            taskId = updatedTask.id,
            title = updatedTask.title,
            description = updatedTask.description,
            cycleDateStr = activeCycle,
            repeatCount = updatedTask.repeatCount,
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            repeatIntervalMinutes = settings.repeatIntervalMinutes
        )
        _activeAlarmFlow.value = state

        val notification = notificationManager.buildAlarmForegroundNotification(
            taskId = updatedTask.id,
            taskTitle = updatedTask.title,
            taskDescription = updatedTask.description,
            cycleDateStr = activeCycle,
            defaultSnoozeMinutes = settings.defaultSnoozeMinutes,
            repeatIntervalMinutes = settings.repeatIntervalMinutes,
            repeatCount = updatedTask.repeatCount
        )

        val notifId = (TaskNotificationManager.NOTIFICATION_ID_BASE + updatedTask.id).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notifId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(notifId, notification)
        }

        startLoudAudio(settings.alarmSound)
        if (settings.vibrationEnabled) {
            startVibration()
        }

        ringTimeoutJob?.cancel()
        ringTimeoutJob = serviceScope.launch {
            delay(90_000L)
            stopAudioAndVibrationOnly()
            notificationManager.showPersistentUncompletedWarning(
                taskId = updatedTask.id,
                taskTitle = updatedTask.title,
                nextRepeatMinutes = settings.repeatIntervalMinutes
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            _activeAlarmFlow.value = null
            releaseWakeLock()
            stopSelf()
        }
    }

    private fun startLoudAudio(toneOption: AlarmToneOption) {
        stopAudioOnly()
        val uri: Uri = when (toneOption) {
            AlarmToneOption.DEFAULT_ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            AlarmToneOption.RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            AlarmToneOption.NOTIFICATION_LOUD -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } ?: return

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVol == 0 && maxVol > 0) {
                runCatching {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        (maxVol * 0.7f).toInt().coerceAtLeast(1),
                        0
                    )
                }
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        stopVibrationOnly()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0L, 600L, 400L, 600L, 800L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DailyTracker::AlarmWakeLock"
        ).apply {
            acquire(2 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun stopAudioOnly() {
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
    }

    private fun stopVibrationOnly() {
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun stopAudioAndVibrationOnly() {
        stopAudioOnly()
        stopVibrationOnly()
    }

    private fun stopPlaybackAndFinish() {
        ringTimeoutJob?.cancel()
        stopAudioAndVibrationOnly()
        _activeAlarmFlow.value = null
        releaseWakeLock()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        ringTimeoutJob?.cancel()
        stopAudioAndVibrationOnly()
        _activeAlarmFlow.value = null
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
