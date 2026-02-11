package com.example.fitnessapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.fitnessapp.ui.theme.FitnessAppTheme
import kotlin.math.round


class MainActivity : ComponentActivity(), SensorEventListener {
    private val sensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    private val sensor: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }

    private val tvStepsCounter: TextView by lazy {
        findViewById(R.id.StepsCounter) }

    private val tvDistanceSpent: TextView by lazy {
        findViewById(R.id.DistanceSpent) }

    private val tvKcalBurned: TextView by lazy {
        findViewById(R.id.KilocaloriesBurned) }

    private val cChronometer: Chronometer by lazy {
        findViewById(R.id.timeSpentChronometer) }

    private val bStartButton: TextView by lazy {
        findViewById(R.id.StartButton) }

    private val bPauseButton: Button by lazy {
        findViewById(R.id.PauseButton) }

    private val bStopButton: Button by lazy {
        findViewById(R.id.StopButton) }

    private val bHistoryButton: TextView by lazy {
        findViewById(R.id.HistoryButton) }

    private val bTasksButton: TextView by lazy {
        findViewById(R.id.TasksButton) }

    private var stepCounter: Float = 0f
    private var stepCounterBase: Float = 0f

    private var currStepCounter: Float = 0f

    private var distanceSpent: Float = 0f

    private var kcalBurned: Float = 0f

    private val kcalBurnedPerStep: Float = 0.04f

    private val distancePerStep: Float = 0.7f

    private var isStatsObserveable = false

    private var isFirstStart = true

    /** Время в мс на момент паузы (используется, когда таймер на паузе). */
    private var elapsedWhenPaused: Long = 0L

    private var isChronometerPaused = false

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshFromPrefsRunnable = object : Runnable {
        override fun run() {
            if (!isStatsObserveable) return
            val p = sessionPrefs()
            currStepCounter = p.getFloat(PREF_CURR_STEPS, 0f)
            distanceSpent = p.getFloat(PREF_DISTANCE, 0f)
            kcalBurned = p.getFloat(PREF_KCAL, 0f)
            isChronometerPaused = p.getBoolean(PREF_PAUSED, false)
            elapsedWhenPaused = p.getLong(PREF_ELAPSED_PAUSED, 0L)
            updateStatsDisplay()
            if (isChronometerPaused) {
                cChronometer.base = SystemClock.elapsedRealtime() - elapsedWhenPaused
                cChronometer.stop()
            } else {
                val base = p.getLong(PREF_CHRONO_BASE, 0L)
                if (base != 0L) {
                    cChronometer.base = base
                    cChronometer.start()
                }
            }
            refreshHandler.postDelayed(this, 1000)
        }
    }

    private fun sessionPrefs(): SharedPreferences =
        getSharedPreferences("session", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Восстановление сессии из памяти (savedInstanceState) или из SharedPreferences
        restoreSessionFromPrefsOrBundle(savedInstanceState)

        bTasksButton.setOnClickListener {
            val intent = Intent(this, TasksActivity::class.java)
            startActivity(intent)
        }

        bHistoryButton.setOnClickListener {
            val intent = Intent(this, SecondActivity::class.java)
            startActivity(intent)
        }

        bStartButton.setOnClickListener {
            // Старт сессии
            isStatsObserveable = true
            isChronometerPaused = false
            stepCounterBase = stepCounter

            tvStepsCounter.text = "Steps: 0"
            tvDistanceSpent.text = "Distance: 0.0 m"
            tvKcalBurned.text = "Burned: 0.0 kcal"

            cChronometer.base = SystemClock.elapsedRealtime()
            cChronometer.start()

            saveSessionToPrefs()
            startSessionService()
            bStartButton.visibility = View.GONE
            bPauseButton.visibility = View.VISIBLE
            bStopButton.visibility = View.VISIBLE
            bPauseButton.text = "Пауза"
        }

        bPauseButton.setOnClickListener {
            if (isChronometerPaused) {
                // Продолжить
                cChronometer.base = SystemClock.elapsedRealtime() - elapsedWhenPaused
                cChronometer.start()
                isChronometerPaused = false
                bPauseButton.text = "Пауза"
            } else {
                // Пауза
                cChronometer.stop()
                elapsedWhenPaused = SystemClock.elapsedRealtime() - cChronometer.base
                isChronometerPaused = true
                bPauseButton.text = "Продолжить"
            }
            saveSessionToPrefs()
        }

        bStopButton.setOnClickListener {
            // Остановка сессии и сохранение в историю
            stopSessionService()
            refreshHandler.removeCallbacks(refreshFromPrefsRunnable)
            // Берём последние значения из префов (сервис мог обновлять их в фоне)
            val p = sessionPrefs()
            currStepCounter = p.getFloat(PREF_CURR_STEPS, currStepCounter)
            distanceSpent = p.getFloat(PREF_DISTANCE, distanceSpent)
            kcalBurned = p.getFloat(PREF_KCAL, kcalBurned)
            cChronometer.stop()
            isStatsObserveable = false
            stepCounterBase = stepCounter

            val elapsedMillis = if (isChronometerPaused) {
                elapsedWhenPaused
            } else {
                SystemClock.elapsedRealtime() - cChronometer.base
            }
            val seconds = (elapsedMillis / 1000) % 60
            val minutes = (elapsedMillis / 1000) / 60
            val elapsedTime = String.format("%02d:%02d", minutes, seconds)

            val db = baseContext.openOrCreateDatabase("app.db", MODE_PRIVATE, null)
            db.execSQL("CREATE TABLE IF NOT EXISTS history (steps STRING, distance STRING, burned STRING, time STRING)")
            db.execSQL(String.format("INSERT OR IGNORE INTO history VALUES ('%s','%s','%s','%s');", currStepCounter.toString(), distanceSpent.toString(), kcalBurned.toString(), elapsedTime))
            db.close()

            currStepCounter = 0f
            distanceSpent = 0f
            kcalBurned = 0f
            isChronometerPaused = false

            clearSessionPrefs()
            bPauseButton.visibility = View.GONE
            bStopButton.visibility = View.GONE
            bStartButton.visibility = View.VISIBLE
            bPauseButton.text = "Пауза"
            bStopButton.text = "Остановка"
        }
    }

    /** Сохраняет состояние сессии в SharedPreferences (чтобы пережить переход в Задачи/Историю). */
    private fun saveSessionToPrefs() {
        val elapsed = if (isChronometerPaused) elapsedWhenPaused else SystemClock.elapsedRealtime() - cChronometer.base
        sessionPrefs().edit()
            .putBoolean(PREF_ACTIVE, true)
            .putLong(PREF_CHRONO_BASE, cChronometer.base)
            .putBoolean(PREF_PAUSED, isChronometerPaused)
            .putLong(PREF_ELAPSED_PAUSED, elapsedWhenPaused)
            .putFloat(PREF_STEP_BASE, stepCounterBase)
            .putFloat(PREF_CURR_STEPS, currStepCounter)
            .putFloat(PREF_DISTANCE, distanceSpent)
            .putFloat(PREF_KCAL, kcalBurned)
            .apply()
    }

    private fun clearSessionPrefs() {
        sessionPrefs().edit().clear().apply()
    }

    /** Восстанавливает сессию из SharedPreferences или из Bundle (при пересоздании активности). */
    private fun restoreSessionFromPrefsOrBundle(savedInstanceState: Bundle?) {
        val prefs = sessionPrefs()
        val fromBundle = savedInstanceState?.getBoolean(KEY_STATS_OBSERVABLE, false) == true
        val fromPrefs = prefs.getBoolean(PREF_ACTIVE, false)

        if (fromBundle && savedInstanceState != null) {
            isStatsObserveable = true
            stepCounterBase = savedInstanceState.getFloat(KEY_STEP_BASE, 0f)
            stepCounter = savedInstanceState.getFloat(KEY_STEP_CURRENT, stepCounterBase)
            isChronometerPaused = savedInstanceState.getBoolean(KEY_CHRONO_PAUSED, false)
            elapsedWhenPaused = savedInstanceState.getLong(KEY_ELAPSED_PAUSED, 0L)
            currStepCounter = savedInstanceState.getFloat(KEY_CURR_STEPS, 0f)
            distanceSpent = savedInstanceState.getFloat(KEY_DISTANCE, 0f)
            kcalBurned = savedInstanceState.getFloat(KEY_KCAL, 0f)
        } else if (fromPrefs) {
            isStatsObserveable = true
            stepCounterBase = prefs.getFloat(PREF_STEP_BASE, 0f)
            isChronometerPaused = prefs.getBoolean(PREF_PAUSED, false)
            elapsedWhenPaused = prefs.getLong(PREF_ELAPSED_PAUSED, 0L)
            currStepCounter = prefs.getFloat(PREF_CURR_STEPS, 0f)
            distanceSpent = prefs.getFloat(PREF_DISTANCE, 0f)
            kcalBurned = prefs.getFloat(PREF_KCAL, 0f)
            stepCounter = stepCounterBase // будет обновлено при первом onSensorChanged
        } else {
            return
        }

        if (isStatsObserveable) {
            if (isChronometerPaused) {
                cChronometer.base = SystemClock.elapsedRealtime() - elapsedWhenPaused
                cChronometer.stop()
            } else {
                val base = if (fromBundle && savedInstanceState != null)
                    savedInstanceState.getLong(KEY_CHRONO_BASE, 0L)
                else
                    prefs.getLong(PREF_CHRONO_BASE, 0L)
                if (base != 0L) {
                    cChronometer.base = base
                }
                cChronometer.start()
            }
            bStartButton.visibility = View.GONE
            bPauseButton.visibility = View.VISIBLE
            bStopButton.visibility = View.VISIBLE
            bPauseButton.text = if (isChronometerPaused) "Продолжить" else "Пауза"
            updateStatsDisplay()
            startSessionService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_STATS_OBSERVABLE, isStatsObserveable)
        outState.putFloat(KEY_STEP_BASE, stepCounterBase)
        outState.putFloat(KEY_STEP_CURRENT, stepCounter)
        outState.putBoolean(KEY_CHRONO_PAUSED, isChronometerPaused)
        outState.putLong(KEY_ELAPSED_PAUSED, elapsedWhenPaused)
        outState.putFloat(KEY_CURR_STEPS, currStepCounter)
        outState.putFloat(KEY_DISTANCE, distanceSpent)
        outState.putFloat(KEY_KCAL, kcalBurned)
        if (isStatsObserveable) {
            val base = if (isChronometerPaused) {
                SystemClock.elapsedRealtime() - elapsedWhenPaused
            } else {
                cChronometer.base
            }
            outState.putLong(KEY_CHRONO_BASE, base)
        }
    }

    override fun onResume() {
        super.onResume()
        // Если сессия была активна (по префам) — восстановить UI и таймер
        if (sessionPrefs().getBoolean(PREF_ACTIVE, false) && !isStatsObserveable) {
            restoreSessionFromPrefsOrBundle(null)
        }
        if (isStatsObserveable) {
            // Сессия идёт: данные считает сервис в фоне, подтягиваем из префов раз в секунду
            syncChronometerAfterReturn()
            refreshFromPrefsRunnable.run()
        } else {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        if (isStatsObserveable) {
            refreshHandler.removeCallbacks(refreshFromPrefsRunnable)
            saveSessionToPrefs()
        } else {
            sensorManager.unregisterListener(this)
        }
        super.onPause()
    }

    private fun startSessionService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
        val intent = Intent(this, SessionForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSessionService() {
        stopService(Intent(this, SessionForegroundService::class.java))
    }

    /** Синхронизирует отображение таймера после возврата на экран (время шло в фоне). */
    private fun syncChronometerAfterReturn() {
        if (isChronometerPaused) return
        val elapsed = SystemClock.elapsedRealtime() - cChronometer.base
        cChronometer.base = SystemClock.elapsedRealtime() - elapsed
        cChronometer.start()
    }

    private fun updateStatsDisplay() {
        tvStepsCounter.text = "Steps: ${currStepCounter.toInt()}"
        tvDistanceSpent.text = "Distance: $distanceSpent m"
        tvKcalBurned.text = "Burned: $kcalBurned kcal"
    }

    companion object {
        private const val PREF_ACTIVE = "session_active"
        private const val PREF_CHRONO_BASE = "chrono_base"
        private const val PREF_PAUSED = "chrono_paused"
        private const val PREF_ELAPSED_PAUSED = "elapsed_paused"
        private const val PREF_STEP_BASE = "step_base"
        private const val PREF_CURR_STEPS = "curr_steps"
        private const val PREF_DISTANCE = "distance"
        private const val PREF_KCAL = "kcal"
        private const val KEY_STATS_OBSERVABLE = "stats_observable"
        private const val KEY_STEP_BASE = "step_base"
        private const val KEY_STEP_CURRENT = "step_current"
        private const val KEY_CHRONO_BASE = "chrono_base"
        private const val KEY_CHRONO_PAUSED = "chrono_paused"
        private const val KEY_ELAPSED_PAUSED = "elapsed_paused"
        private const val KEY_CURR_STEPS = "curr_steps"
        private const val KEY_DISTANCE = "distance"
        private const val KEY_KCAL = "kcal"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null) {
            tvStepsCounter.text = "onSensorChangedEvent == NULL"
            return
        }

        stepCounter = event.values[0]

        if (isFirstStart) {
            stepCounterBase = stepCounter
            isFirstStart = false
        }

        currStepCounter = stepCounter - stepCounterBase

        val steps = currStepCounter.toInt()

        distanceSpent = round(distancePerStep * currStepCounter)
        kcalBurned = round(kcalBurnedPerStep * currStepCounter)

        if (!isStatsObserveable) { return }

        tvStepsCounter.text = "Steps: $steps"
        tvDistanceSpent.text = "Distance: $distanceSpent m"
        tvKcalBurned.text = "Burned: $kcalBurned kcal"

        // Авто-выполнение пользовательских задач (дистанция, калории, шаги, время)
        if (isStatsObserveable) {
            val elapsedMillis = if (isChronometerPaused) {
                elapsedWhenPaused
            } else {
                SystemClock.elapsedRealtime() - cChronometer.base
            }
            val elapsedMinutes = (elapsedMillis / 1000 / 60).toInt()
            TaskStorage.markCompletedIfReached(
                this,
                distanceMeters = distanceSpent,
                kcalBurned = kcalBurned,
                steps = steps,
                elapsedMinutes = elapsedMinutes
            )
        }
    }
}