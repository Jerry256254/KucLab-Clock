package dev.kuclab.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            updateWidget(context, id)
            scheduleNextTick(context, id)
        }
    }

    override fun onDisabled(context: Context) {
        // Best-effort cleanup: by the time the last widget is removed, the OS no longer
        // reports its id, so we sweep a generous range of the request codes scheduleNextTick
        // could have used. Any tick alarm outside this range self-terminates anyway, since
        // ACTION_TICK's handler is a no-op once the widget id is no longer registered.
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 0..500) {
            val pi = PendingIntent.getBroadcast(
                context, 40_000 + i, tickIntent(context, i),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, ClockWidgetProvider::class.java)
                )
                if (id >= 0 && ids.contains(id)) {
                    updateWidget(context, id)
                    scheduleNextTick(context, id)
                }
            }
            ACTION_TICK_ALL -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(context, ClockWidgetProvider::class.java)
                )
                ids.forEach { id ->
                    updateWidget(context, id)
                    scheduleNextTick(context, id)
                }
            }
        }
    }

    /**
     * The clock face is a [android.widget.Chronometer], not a plain TextView refreshed every
     * second from here: a Chronometer ticks on its own inside the launcher's process once
     * armed, so the seconds display is immune to this app's background-alarm throttling
     * (the previous TextView-based version could only get an actual per-second update in
     * roughly every 5th tick once Android started batching/deferring this app's frequent
     * background alarms - a Chronometer has no such dependency at all).
     *
     * Its base is set so elapsed-since-base equals milliseconds-since-midnight, i.e. it
     * literally counts up through today's wall-clock time. DateUtils' elapsed-time formatter
     * omits the hour digits below 1h and never zero-pads a single-digit hour, so the wrapping
     * `format` string is swapped once per hour boundary to paper over both cases.
     */
    private fun updateWidget(context: Context, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.clock_widget_layout)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val midnight = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val millisSinceMidnight = now - midnight
        val base = SystemClock.elapsedRealtime() - millisSinceMidnight
        val format = when {
            hour == 0 -> "00:%s"
            hour < 10 -> "0%s"
            else -> "%s"
        }
        views.setChronometer(R.id.widget_time, base, format, true)
        views.setTextViewText(R.id.widget_status, nearestEventText(context, now))
        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
    }

    /** Whichever of timer/stopwatch/next-alarm is the soonest-relevant thing right now,
     * falling back to today's date when nothing is scheduled or running. */
    private fun nearestEventText(context: Context, now: Long): String {
        val timer = TimerState.load(context)
        if (timer.running && timer.endAtWallClock > now) {
            return "Časovač: zbývá ${formatShortDuration(timer.endAtWallClock - now)}"
        }
        val sw = StopwatchState.load(context)
        if (sw.running) {
            val elapsed = sw.accumulatedMs + (now - sw.startAtWallClock)
            return "Stopky: běží ${formatClock(elapsed)}"
        }
        val next = AlarmScheduler.nextUpcoming(context)
        if (next != null) {
            val (alarm, at) = next
            val time = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute)
            return "Příští budík za ${formatShortDuration(at - now)} ($time)"
        }
        return SimpleDateFormat("EEE d. MMM yyyy", Locale("cs")).format(java.util.Date(now))
    }

    private fun formatClock(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun formatShortDuration(ms: Long): String {
        val totalMin = (ms + 59_999) / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h} h ${m} min" else "${m} min"
    }

    private fun tickIntent(context: Context, widgetId: Int): Intent =
        Intent(context, ClockWidgetProvider::class.java)
            .setAction(ACTION_TICK)
            .putExtra(EXTRA_WIDGET_ID, widgetId)

    /** While a timer or stopwatch is actively running, the status line is refreshed every
     * 30s so it stays reasonably current; otherwise the only thing that can change it is an
     * hour boundary (chronometer format bucket) or a new day, so ticks back off to hourly -
     * a small, fixed number of background alarms per day regardless of how many widgets or
     * how long the app goes unopened. */
    private fun scheduleNextTick(context: Context, widgetId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val active = TimerState.load(context).running || StopwatchState.load(context).running
        val at = if (active) {
            now + 30_000L
        } else {
            Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val pi = PendingIntent.getBroadcast(
            context, 40_000 + widgetId, tickIntent(context, widgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        }
    }

    companion object {
        const val ACTION_TICK = "dev.kuclab.clock.action.WIDGET_TICK"
        const val ACTION_TICK_ALL = "dev.kuclab.clock.action.WIDGET_TICK_ALL"
        private const val EXTRA_WIDGET_ID = "widgetId"
    }
}
