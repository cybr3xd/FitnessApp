package com.example.fitnessapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class TasksActivity : ComponentActivity() {

    private val lvTasks: ListView by lazy {
        findViewById(R.id.TasksList)
    }

    private val bBackButton: Button by lazy {
        findViewById(R.id.BackTasksButton)
    }

    private val etTaskTitle: EditText by lazy {
        findViewById(R.id.TaskTitleInput)
    }

    private val spinnerType: Spinner by lazy {
        findViewById(R.id.TaskTypeSpinner)
    }

    private val etTargetValue: EditText by lazy {
        findViewById(R.id.TaskTargetValueInput)
    }

    private val bAddTask: Button by lazy {
        findViewById(R.id.AddTaskButton)
    }

    private val taskTypes = enumValues<TaskType>().toList()

    private var tasksAdapter: TasksAdapter? = null

    private fun renderTasks() {
        val tasks = TaskStorage.loadTasks(this)
        tasksAdapter = TasksAdapter(tasks) {
            renderTasks()
        }
        lvTasks.adapter = tasksAdapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_tasks)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tasks_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val typeLabels = taskTypes.map { type ->
            when (type) {
                TaskType.DISTANCE -> getString(R.string.task_type_distance)
                TaskType.CALORIES -> getString(R.string.task_type_calories)
                TaskType.STEPS -> getString(R.string.task_type_steps)
                TaskType.TIME -> getString(R.string.task_type_time)
            }
        }
        val spinnerAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, typeLabels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = spinnerAdapter

        bBackButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        bAddTask.setOnClickListener {
            val targetValue = etTargetValue.text?.toString()?.trim()?.toIntOrNull()
            val type = taskTypes.getOrNull(spinnerType.selectedItemPosition) ?: taskTypes.first()
            if (targetValue != null && targetValue > 0) {
                TaskStorage.addTask(
                    context = this,
                    type = type,
                    title = etTaskTitle.text?.toString().orEmpty(),
                    targetValue = targetValue
                )
                etTaskTitle.setText("")
                etTargetValue.setText("")
                renderTasks()
            }
        }

        renderTasks()
    }

    override fun onResume() {
        super.onResume()
        renderTasks()
    }

    private inner class TasksAdapter(
        private var tasks: List<Task>,
        private val onChanged: () -> Unit
    ) : BaseAdapter() {

        override fun getCount(): Int = tasks.size

        override fun getItem(position: Int): Task = tasks[position]

        override fun getItemId(position: Int): Long = tasks[position].id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@TasksActivity)
                .inflate(R.layout.item_task, parent, false)
            val task = getItem(position)

            val tvTitle = view.findViewById<TextView>(R.id.item_task_title)
            val bToggleDone = view.findViewById<Button>(R.id.item_task_toggle_done)
            val bDelete = view.findViewById<Button>(R.id.item_task_delete)

            val status = if (task.completed) "✅" else "⬜"
            tvTitle.text = "$status ${task.title} — ${task.targetLabel()}"

            bToggleDone.text = if (task.completed) {
                getString(R.string.task_done)
            } else {
                getString(R.string.task_not_done)
            }

            bToggleDone.setOnClickListener {
                TaskStorage.setTaskCompleted(this@TasksActivity, task.id, !task.completed)
                onChanged()
            }

            bDelete.setOnClickListener {
                TaskStorage.deleteTask(this@TasksActivity, task.id)
                onChanged()
            }

            return view
        }
    }
}
