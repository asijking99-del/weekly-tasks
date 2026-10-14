package com.example.weeklytasks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** day uses java.util.Calendar constants: SUNDAY = 1 ... SATURDAY = 7 */
data class Task(val id: Int, val title: String, val day: Int, val hour: Int, val minute: Int)

object Store {
    private const val PREFS = "tasks"
    private const val KEY = "list"

    fun load(ctx: Context): MutableList<Task> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<Task>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Task(
                    o.getInt("id"),
                    o.getString("title"),
                    o.getInt("day"),
                    o.getInt("hour"),
                    o.getInt("minute")
                )
            )
        }
        return out
    }

    fun save(ctx: Context, tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("day", t.day)
                    .put("hour", t.hour)
                    .put("minute", t.minute)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

fun fmtTime(hour: Int, minute: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val ampm = if (hour < 12) "AM" else "PM"
    return String.format("%d:%02d %s", h12, minute, ampm)
}
