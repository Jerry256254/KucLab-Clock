package dev.kuclab.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Small SharedPreferences-backed snapshots of "is the timer/stopwatch currently running,
 * and since/until when" - written by TimerScreen/StopwatchScreen whenever their state
 * changes, and read by ClockWidgetProvider (a different process context with no access to
 * Compose state) so the home-screen widget can show whichever of alarm/timer/stopwatch is
 * the soonest-relevant thing happening, matching the in-app screens.
 */
object TimerState {
    private const val PREFS = "timer_state"
    private const val KEY_RUNNING = "running"
    private const val KEY_END_AT = "endAt"
    private const val KEY_TOTAL = "totalMs"

    data class Snapshot(val running: Boolean, val endAtWallClock: Long, val totalMs: Long)

    fun save(context: Context, running: Boolean, endAtWallClock: Long, totalMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, running)
            .putLong(KEY_END_AT, endAtWallClock)
            .putLong(KEY_TOTAL, totalMs)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            running = p.getBoolean(KEY_RUNNING, false),
            endAtWallClock = p.getLong(KEY_END_AT, 0L),
            totalMs = p.getLong(KEY_TOTAL, 0L)
        )
    }
}

object StopwatchState {
    private const val PREFS = "stopwatch_state"
    private const val KEY_RUNNING = "running"
    private const val KEY_START_AT = "startAt"
    private const val KEY_ACCUMULATED = "accumulated"

    data class Snapshot(val running: Boolean, val startAtWallClock: Long, val accumulatedMs: Long)

    fun save(context: Context, running: Boolean, startAtWallClock: Long, accumulatedMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, running)
            .putLong(KEY_START_AT, startAtWallClock)
            .putLong(KEY_ACCUMULATED, accumulatedMs)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            running = p.getBoolean(KEY_RUNNING, false),
            startAtWallClock = p.getLong(KEY_START_AT, 0L),
            accumulatedMs = p.getLong(KEY_ACCUMULATED, 0L)
        )
    }
}

/** Central place to ask every KucLab Clock home-screen widget to redraw itself soon -
 * called after any change that could affect the widget's "nearest event" line (alarm
 * saved/toggled, timer started/stopped, stopwatch started/stopped). */
object WidgetRefresh {
    fun requestUpdate(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 424242,
            Intent(context, ClockWidgetProvider::class.java)
                .setAction(ClockWidgetProvider.ACTION_TICK_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 300, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + 300, pi)
        }
    }

    fun hasWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        return mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java)).isNotEmpty()
    }
}
