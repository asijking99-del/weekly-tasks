package com.example.weeklytasks

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {

    private fun pending(ctx: Context, id: Int): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next moment (strictly after now) that falls on the task's weekday and time. */
    fun nextTime(t: Task): Long {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, t.hour)
        c.set(Calendar.MINUTE, t.minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        while (c.get(Calendar.DAY_OF_WEEK) != t.day || c.timeInMillis <= now) {
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun schedule(ctx: Context, t: Task) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextTime(t)
        val pi = pending(ctx, t.id)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, id))
    }

    fun scheduleAll(ctx: Context) {
        Store.load(ctx).forEach { schedule(ctx, it) }
    }
}

object Notifier {
    private const val CHANNEL = "task_reminders"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(ch)
    }

    fun show(ctx: Context, t: Task) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(t.title)
            .setContentText("It's time: " + fmtTime(t.hour, t.minute))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(t.id, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val task = Store.load(ctx).firstOrNull { it.id == id } ?: return
        Notifier.show(ctx, task)
        Scheduler.schedule(ctx, task) // repeat next week
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleAll(ctx)
    }
}
