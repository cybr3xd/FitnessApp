package com.example.fitnessapp

import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView


class SecondActivity : ComponentActivity() {

    private val lvHistory: ListView by lazy {
        findViewById(com.example.fitnessapp.R.id.HistoryList) }

    private val bBackButton: TextView by lazy {
        findViewById(R.id.BackButton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_second)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bBackButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        var Stats: ArrayList<String> = arrayListOf()

        // Stats.add("ABOBA")

        val db = baseContext.openOrCreateDatabase("app.db", MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS history (steps STRING, distance STRING, burned STRING, time STRING)")

        val query = db.rawQuery("SELECT * FROM history;", null)
        while (query.moveToNext()) {
            val steps = query.getString(0)
            val distance = query.getString(1)
            val burned = query.getString(2)
            val time = query.getString(3)

            val TextLine = String.format("Steps: %s Distance: %s Burned: %s Time: %s", steps,distance,burned,time)

            Stats.add(TextLine)
        }
        query.close()
        db.close()

        var adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, Stats.toArray())

        lvHistory.setAdapter(adapter)
    }
}