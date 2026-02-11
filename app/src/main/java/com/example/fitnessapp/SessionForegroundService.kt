package com.example.fitnessapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlin.math.round

/**
 * Фоновый сервис сессии: считает шаги, дистанцию, ккал и время, пока приложение свёрнуто.
 * Останавливается только по нажатию «Остановка» или при завершении приложения.
 */
class SessionForegroundService : Service(), SensorEventListener {

    private val prefs by lazy { getSharedPreferences("session", Context.MODE_PRIVATE) }
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val stepSensor: Sensor? by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }

    private val handler = Handler(Looper.getMainLooper())
    private val distancePerStep = 0.7f
    private val kcalBurnedPerStep = 0.04f

    private var stepCounterBase: Float = 0f
    private var stepCounter: Float = 0f

    override fun onCreate() {
        super.onCreate()
        stepCounterBase = prefs.getFloat("step_base", 0f)
        stepCounter = stepCounterBase
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0, 0f, 0f))
        stepCounterBase = prefs.getFloat("step_base", 0f)
        stepCounter = stepCounterBase
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        startNotificationUpdater()
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(notificationUpdateRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        stepCounter = event.values[0]
        val currSteps = (stepCounter - stepCounterBase).coerceAtLeast(0f)
        val distance = round(distancePerStep * currSteps)
        val kcal = round(kcalBurnedPerStep * currSteps)
        prefs.edit()
            .putFloat("curr_steps", currSteps)
            .putFloat("distance", distance)
            .putFloat("kcal", kcal)
            .apply()
        if (!prefs.getBoolean("chrono_paused", false)) {
            val base = prefs.getLong("chrono_base", 0L)
            val elapsedMs = if (base == 0L) 0L else SystemClock.elapsedRealtime() - base
            val elapsedMinutes = (elapsedMs / 1000 / 60).toInt()
            TaskStorage.markCompletedIfReached(this, distance, kcal, currSteps.toInt(), elapsedMinutes)
        }
        handler.post { updateNotification(currSteps.toInt(), distance, kcal) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startNotificationUpdater() {
        handler.post(notificationUpdateRunnable)
    }

    /** Раз в секунду обновляет уведомление: таймер и статистика в реальном времени. */
    private val notificationUpdateRunnable = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean("session_active", false)) return
            val steps = prefs.getFloat("curr_steps", 0f).toInt()
            val distance = prefs.getFloat("distance", 0f)
            val kcal = prefs.getFloat("kcal", 0f)
            updateNotification(steps, distance, kcal)
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateNotification(steps: Int, distance: Float, kcal: Float) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(steps, distance, kcal))
    }

    private fun buildNotification(steps: Int, distance: Float, kcal: Float): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val elapsed = if (prefs.getBoolean("chrono_paused", false)) {
            prefs.getLong("elapsed_paused", 0L)
        } else {
            val base = prefs.getLong("chrono_base", 0L)
            if (base == 0L) 0L else SystemClock.elapsedRealtime() - base
        }
        val sec = (elapsed / 1000) % 60
        val min = (elapsed / 1000) / 60
        val timeStr = String.format("%02d:%02d", min, sec)
        val text = "$timeStr · $steps шагов · ${distance.toInt()} м · ${kcal.toInt()} ккал"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Тренировка идёт")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Сессия тренировки",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "session_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.fitnessapp.STOP_SESSION"
    }
}
