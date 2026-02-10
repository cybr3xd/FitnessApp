package com.example.fitnessapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class TaskType(val key: String) {
    DISTANCE("distance"),
    CALORIES("calories"),
    STEPS("steps"),
    TIME("time");

    companion object {
        fun fromKey(key: String) = enumValues<TaskType>().find { it.key == key } ?: DISTANCE
    }
}

data class Task(
    val id: String,
    val title: String,
    val type: TaskType,
    val targetValue: Int, // метры / ккал / шаги / минуты
    val completed: Boolean
) {
    fun targetLabel(): String = when (type) {
        TaskType.DISTANCE -> "$targetValue м"
        TaskType.CALORIES -> "$targetValue ккал"
        TaskType.STEPS -> "$targetValue шагов"
        TaskType.TIME -> "$targetValue мин"
    }
}

object TaskStorage {
    private const val PREFS_NAME = "tasks_prefs"
    private const val KEY_TASKS = "tasks_v2"

    fun loadTasks(context: Context): List<Task> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val type = TaskType.fromKey(obj.optString("type", TaskType.DISTANCE.key))
                Task(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    type = type,
                    targetValue = obj.optInt("targetValue", 0),
                    completed = obj.optBoolean("completed", false)
                ).takeIf { it.id.isNotBlank() && it.targetValue > 0 }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveTasks(context: Context, tasks: List<Task>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        tasks.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("type", t.type.key)
            obj.put("targetValue", t.targetValue)
            obj.put("completed", t.completed)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    fun addTask(context: Context, type: TaskType, title: String, targetValue: Int) {
        val safeTarget = targetValue.coerceAtLeast(1)
        val defaultTitle = when (type) {
            TaskType.DISTANCE -> "Пробежать $safeTarget м"
            TaskType.CALORIES -> "Сжечь $safeTarget ккал"
            TaskType.STEPS -> "Сделать $safeTarget шагов"
            TaskType.TIME -> "Заниматься $safeTarget мин"
        }
        val safeTitle = title.trim().ifBlank { defaultTitle }
        val existing = loadTasks(context)
        val newTask = Task(
            id = System.currentTimeMillis().toString(),
            title = safeTitle,
            type = type,
            targetValue = safeTarget,
            completed = false
        )
        saveTasks(context, existing + newTask)
    }

    fun deleteTask(context: Context, taskId: String) {
        val tasks = loadTasks(context).filter { it.id != taskId }
        saveTasks(context, tasks)
    }

    fun setTaskCompleted(context: Context, taskId: String, completed: Boolean) {
        val tasks = loadTasks(context).map { t ->
            if (t.id == taskId) t.copy(completed = completed) else t
        }
        saveTasks(context, tasks)
    }

    /**
     * Отмечает задачи выполненными, когда текущие показатели достигают цели.
     * elapsedMinutes — время сессии в минутах (от chronometer).
     */
    fun markCompletedIfReached(
        context: Context,
        distanceMeters: Float,
        kcalBurned: Float,
        steps: Int,
        elapsedMinutes: Int
    ): Boolean {
        val tasks = loadTasks(context)
        if (tasks.isEmpty()) return false

        var changed = false
        val updated = tasks.map { t ->
            if (t.completed) return@map t
            val reached = when (t.type) {
                TaskType.DISTANCE -> distanceMeters >= t.targetValue
                TaskType.CALORIES -> kcalBurned >= t.targetValue
                TaskType.STEPS -> steps >= t.targetValue
                TaskType.TIME -> elapsedMinutes >= t.targetValue
            }
            if (reached) {
                changed = true
                t.copy(completed = true)
            } else t
        }

        if (changed) saveTasks(context, updated)
        return changed
    }
}
