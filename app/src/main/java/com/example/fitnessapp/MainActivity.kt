package com.example.fitnessapp

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

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
        }

        bStopButton.setOnClickListener {
            // Остановка сессии и сохранение в историю
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

            bPauseButton.visibility = View.GONE
            bStopButton.visibility = View.GONE
            bStartButton.visibility = View.VISIBLE
            bPauseButton.text = "Пауза"
            bStopButton.text = "Остановка"
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
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