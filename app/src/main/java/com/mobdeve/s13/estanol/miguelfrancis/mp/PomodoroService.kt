package com.mobdeve.s13.estanol.miguelfrancis.mp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.os.IBinder
import android.media.MediaPlayer
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PomodoroService : Service() {

    companion object {
        private const val DEFAULT_WORK_TIME: Long = 25 * 60 * 1000
        private const val DEFAULT_SHORT_BREAK_TIME: Long = 5 * 60 * 1000
        private const val DEFAULT_LONG_BREAK_TIME: Long = 15 * 60 * 1000
        private const val LONG_BREAK_INTERVAL = 4

        const val ACTION_START = "START"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_STOP = "STOP"
        const val ACTION_RESET = "RESET"
        const val ACTION_STATUS = "STATUS"
        const val BROADCAST_UPDATE = "com.mobdeve.s13.estanol.miguelfrancis.mp.UPDATE"
        const val EXTRA_TIME_LEFT = "TIME_LEFT"
        const val EXTRA_IS_RUNNING = "IS_RUNNING"
        const val EXTRA_IS_WORK_PHASE = "IS_WORK_PHASE"
        const val EXTRA_PHASE = "PHASE"
        const val EXTRA_TOTAL_DURATION = "TOTAL_DURATION"
        const val EXTRA_SESSION_COUNT = "SESSION_COUNT"
        const val EXTRA_TOTAL_COMPLETED = "TOTAL_COMPLETED"
        const val NOTIFICATION_CHANNEL_ID = "pomodoro_timer_channel"
        const val NOTIFICATION_ID = 1
    }

    private enum class Phase { WORK, SHORT_BREAK, LONG_BREAK }

    private var timer: CountDownTimer? = null
    private var isRunning = false
    private var phase: Phase = Phase.WORK
    private var timeLeftInMillis: Long = DEFAULT_WORK_TIME
    private var targetEndTime: Long = 0L
    private var workSessionsSinceLongBreak = 0
    private var totalCompletedSessions = 0
    private val gson = Gson()

    private var workEndAlarm: MediaPlayer? = null
    private var breakEndAlarm: MediaPlayer? = null

    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        sharedPreferences = getSharedPreferences("PomodoroSettings", Context.MODE_PRIVATE)

        loadAlarmSounds()
        restoreState()
    }

    private fun loadAlarmSounds() {
        val workAlarmIndex = sharedPreferences.getInt("work_alarm", 0)
        val breakAlarmIndex = sharedPreferences.getInt("break_alarm", 0)

        workEndAlarm = createMediaPlayer(AlarmSoundMappings.workAlarmSounds[workAlarmIndex] ?: R.raw.eightbit_1)
        breakEndAlarm = createMediaPlayer(AlarmSoundMappings.breakAlarmSounds[breakAlarmIndex] ?: R.raw.eightbit_2)
    }

    private fun createMediaPlayer(resourceId: Int): MediaPlayer {
        return MediaPlayer.create(this, resourceId)
    }

    private fun getWorkDuration(): Long {
        val minutes = sharedPreferences.getInt("work_duration", 25)
        return minutes * 60 * 1000L
    }

    private fun getRestDuration(): Long {
        val minutes = sharedPreferences.getInt("break_duration", 5)
        return minutes * 60 * 1000L
    }

    private fun getLongBreakDuration(): Long {
        val minutes = sharedPreferences.getInt("long_break_duration", 15)
        return minutes * 60 * 1000L
    }

    private fun getDurationForPhase(phase: Phase): Long = when (phase) {
        Phase.WORK -> getWorkDuration()
        Phase.SHORT_BREAK -> getRestDuration()
        Phase.LONG_BREAK -> getLongBreakDuration()
    }

    private fun restoreState() {
        phase = runCatching {
            Phase.valueOf(sharedPreferences.getString("state_phase", Phase.WORK.name) ?: Phase.WORK.name)
        }.getOrDefault(Phase.WORK)
        timeLeftInMillis = sharedPreferences.getLong("state_time_left", getDurationForPhase(phase))
        isRunning = sharedPreferences.getBoolean("state_is_running", false)
        workSessionsSinceLongBreak = sharedPreferences.getInt("state_session_progress", 0)
        totalCompletedSessions = sharedPreferences.getInt("total_pomodoro_count", 0)
        targetEndTime = sharedPreferences.getLong("state_target_end_time", 0L)

        if (isRunning && targetEndTime == 0L) {
            // Stale state (app killed mid-run) — clear running flag
            isRunning = false
        }

        if (isRunning && targetEndTime > 0L) {
            val remaining = (targetEndTime - System.currentTimeMillis()).coerceAtLeast(0L)
            isRunning = false
            timeLeftInMillis = remaining
            startTimer()
        } else if (timeLeftInMillis <= 0L) {
            timeLeftInMillis = getDurationForPhase(phase)
            saveState()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESET -> resetPhaseTimer()
            ACTION_STOP -> stopTimer()
            ACTION_STATUS -> broadcastUpdate()
        }
        return START_STICKY
    }

    private fun startTimer() {
        if (isRunning && timer != null) return
        if (timeLeftInMillis <= 0L) {
            timeLeftInMillis = getDurationForPhase(phase)
        }

        timer?.cancel()
        val endTime = System.currentTimeMillis() + timeLeftInMillis
        targetEndTime = endTime
        val notification = buildNotification().build()

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
                broadcastUpdate()
                saveState()
                updateNotification()
            }

            override fun onFinish() {
                timeLeftInMillis = 0L
                handleTimerFinish()
            }
        }.start()

        isRunning = true
        broadcastUpdate()
        startForeground(NOTIFICATION_ID, notification)
        saveState()
    }

    private fun pauseTimer() {
        if (!isRunning) return
        timer?.cancel()
        isRunning = false
        targetEndTime = 0L
        broadcastUpdate()
        saveState()
        updateNotification()
    }

    private fun resetPhaseTimer() {
        timer?.cancel()
        isRunning = false
        targetEndTime = 0L
        timeLeftInMillis = getDurationForPhase(phase)
        broadcastUpdate()
        saveState()
        updateNotification()
    }

    private fun stopTimer() {
        timer?.cancel()
        isRunning = false
        phase = Phase.WORK
        workSessionsSinceLongBreak = 0
        targetEndTime = 0L
        timeLeftInMillis = getDurationForPhase(phase)
        broadcastUpdate()
        saveState()
        stopForeground(true)
        stopSelf()
    }

    private fun handleTimerFinish() {
        isRunning = false
        timer?.cancel()
        targetEndTime = 0L

        if (phase == Phase.WORK) {
            workEndAlarm?.start()
            workSessionsSinceLongBreak += 1
            totalCompletedSessions += 1
            updateStatsOnPomodoroCompletion()

            val shouldLongBreak = workSessionsSinceLongBreak % LONG_BREAK_INTERVAL == 0
            phase = if (shouldLongBreak) Phase.LONG_BREAK else Phase.SHORT_BREAK
            if (shouldLongBreak) workSessionsSinceLongBreak = 0
        } else {
            breakEndAlarm?.start()
            phase = Phase.WORK
        }

        timeLeftInMillis = getDurationForPhase(phase)
        saveState()
        broadcastUpdate()
        updateNotification()
    }

    private fun broadcastUpdate() {
        val intent = Intent(BROADCAST_UPDATE).apply {
            putExtra(EXTRA_TIME_LEFT, timeLeftInMillis)
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_IS_WORK_PHASE, phase == Phase.WORK)
            putExtra(EXTRA_PHASE, phase.name)
            putExtra(EXTRA_TOTAL_DURATION, getDurationForPhase(phase))
            putExtra(EXTRA_SESSION_COUNT, workSessionsSinceLongBreak)
            putExtra(EXTRA_TOTAL_COMPLETED, totalCompletedSessions)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(): NotificationCompat.Builder {
        val startIntent = Intent(this, PomodoroService::class.java).apply { action = ACTION_START }
        val pauseIntent = Intent(this, PomodoroService::class.java).apply { action = ACTION_PAUSE }
        val stopIntent = Intent(this, PomodoroService::class.java).apply { action = ACTION_STOP }
        val resetIntent = Intent(this, PomodoroService::class.java).apply { action = ACTION_RESET }
        val openAppIntent = Intent(this, MainActivity::class.java)

        val startPendingIntent = PendingIntent.getService(
            this, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resetPendingIntent = PendingIntent.getService(
            this, 3, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 4, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Pomodoro ${phaseLabel()}")
            .setContentText("Time remaining: ${formatTime(timeLeftInMillis)}")
            .setSmallIcon(R.drawable.ic_home_black_24dp)
            .setContentIntent(contentIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)

        if (isRunning) {
            builder.addAction(R.drawable.ic_pause, "Pause", pausePendingIntent)
            builder.addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
        } else {
            builder.addAction(R.drawable.ic_start, "Start", startPendingIntent)
            builder.addAction(R.drawable.ic_stop, "Reset", resetPendingIntent)
        }

        return builder
    }

    @SuppressLint("NotificationPermission")
    private fun updateNotification() {
        val notification = buildNotification()
            .setContentText("Time remaining: ${formatTime(timeLeftInMillis)}")
            .build()

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Pomodoro Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(timeInMillis: Long): String {
        val minutes = (timeInMillis / 1000) / 60
        val seconds = (timeInMillis / 1000) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun phaseLabel(): String = when (phase) {
        Phase.WORK -> "Focus"
        Phase.SHORT_BREAK -> "Break"
        Phase.LONG_BREAK -> "Long Break"
    }

    private fun saveState() {
        sharedPreferences.edit()
            .putString("state_phase", phase.name)
            .putLong("state_time_left", timeLeftInMillis)
            .putBoolean("state_is_running", isRunning)
            .putInt("state_session_progress", workSessionsSinceLongBreak)
            .putLong("state_target_end_time", targetEndTime)
            .putInt("total_pomodoro_count", totalCompletedSessions)
            .apply()
    }

    @SuppressLint("NewApi")
    private fun updateStatsOnPomodoroCompletion() {
        val editor = sharedPreferences.edit()
        val currentDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        val mapJson = sharedPreferences.getString("session_history_map", "{}")
        val map: MutableMap<String, Int> = if (mapJson.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            gson.fromJson(mapJson, object : TypeToken<MutableMap<String, Int>>() {}.type)
        }

        map[currentDate] = (map[currentDate] ?: 0) + 1

        val cutoffDate = LocalDate.now().minusDays(27)
        map.entries.removeIf { (date, _) -> LocalDate.parse(date).isBefore(cutoffDate) }

        val lastSevenCount = map
            .filterKeys { key ->
                val date = LocalDate.parse(key)
                !date.isBefore(LocalDate.now().minusDays(6))
            }
            .values
            .sum()

        editor.putString("session_history_map", gson.toJson(map))
        editor.putInt("daily_pomodoro_count", map[currentDate] ?: 0)
        editor.putInt("weekly_pomodoro_count", lastSevenCount)
        editor.putInt("total_pomodoro_count", totalCompletedSessions)
        editor.apply()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        timer?.cancel()

        workEndAlarm?.release()
        workEndAlarm = null

        breakEndAlarm?.release()
        breakEndAlarm = null
    }

}
