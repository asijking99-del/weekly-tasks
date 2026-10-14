package com.example.weeklytasks

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var list: LinearLayout

    private val dayOrder = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    private val dayNames = mapOf(
        Calendar.MONDAY to "Monday", Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday", Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday", Calendar.SATURDAY to "Saturday",
        Calendar.SUNDAY to "Sunday"
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(list)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val add = Button(this).apply {
            text = "+ Add task"
            setOnClickListener { showAddDialog() }
        }
        root.addView(
            add,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) }
        )

        setContentView(root)
        Scheduler.scheduleAll(this)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val tasks = Store.load(this)

        for (day in dayOrder) {
            list.addView(TextView(this).apply {
                text = dayNames[day]
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            })

            val todays = tasks.filter { it.day == day }
                .sortedBy { it.hour * 60 + it.minute }

            if (todays.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = "No tasks"
                    setTextColor(Color.GRAY)
                })
            }

            for (t in todays) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(
                    TextView(this).apply {
                        text = fmtTime(t.hour, t.minute) + "   " + t.title
                        textSize = 16f
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                row.addView(Button(this).apply {
                    text = "✕"
                    setOnClickListener { confirmDelete(t) }
                })
                list.addView(row)
            }
        }
    }

    private fun confirmDelete(t: Task) {
        AlertDialog.Builder(this)
            .setTitle("Delete task?")
            .setMessage(fmtTime(t.hour, t.minute) + "  " + t.title)
            .setPositiveButton("Delete") { _, _ ->
                Scheduler.cancel(this, t.id)
                val rest = Store.load(this).filter { it.id != t.id }
                Store.save(this, rest)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }

        val title = EditText(this).apply {
            hint = "Task (e.g. Gym)"
            setSingleLine()
        }
        box.addView(title)

        val dayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val toggles = dayOrder.map { d ->
            val short = dayNames[d]!!.take(3)
            ToggleButton(this).apply {
                textOn = short
                textOff = short
                text = short
                textSize = 11f
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minimumWidth = 0
            }.also {
                dayRow.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        box.addView(dayRow)

        var hour = 16
        var minute = 0
        val timeBtn = Button(this).apply { text = fmtTime(hour, minute) }
        timeBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                timeBtn.text = fmtTime(h, m)
            }, hour, minute, false).show()
        }
        box.addView(timeBtn)

        val dlg = AlertDialog.Builder(this)
            .setTitle("New weekly task")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dlg.show()

        // Custom click so the dialog stays open if the input is incomplete.
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = title.text.toString().trim()
            val days = dayOrder.filterIndexed { i, _ -> toggles[i].isChecked }
            if (name.isEmpty() || days.isEmpty()) {
                Toast.makeText(this, "Enter a name and pick at least one day", Toast.LENGTH_SHORT).show()
            } else {
                val all = Store.load(this)
                var nextId = (all.maxOfOrNull { it.id } ?: 0) + 1
                for (d in days) {
                    val t = Task(nextId++, name, d, hour, minute)
                    all.add(t)
                    Scheduler.schedule(this, t)
                }
                Store.save(this, all)
                render()
                dlg.dismiss()
            }
        }
    }
}
