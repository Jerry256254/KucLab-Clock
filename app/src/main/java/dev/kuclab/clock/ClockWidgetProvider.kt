package dev.kuclab.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAB_HODINY = 0
private const val TAB_BUDIK = 1
private const val TAB_STOPKY = 2
private const val TAB_CASOVAC = 3

/** Whichever of timer/stopwatch/next-alarm is the soonest-relevant thing right now (plus
 * which app tab it corresponds to, for the widget's tap target), falling back to today's
 * date/the clock tab when nothing is scheduled or running. */
private fun nearestEvent(context: Context, now: Long): Pair<String, Int> {
    val timer = TimerState.load(context)
    if (timer.running && timer.endAtWallClock > now) {
        return "Časovač: zbývá ${formatShortDuration(timer.endAtWallClock - now)}" to TAB_CASOVAC
    }
    val sw = StopwatchState.load(context)
    if (sw.running) {
        val elapsed = sw.accumulatedMs + (now - sw.startAtWallClock)
        return "Stopky: běží ${formatClock(elapsed)}" to TAB_STOPKY
    }
    val next = AlarmScheduler.nextUpcoming(context)
    if (next != null) {
        val (alarm, at) = next
        val time = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute)
        return "Příští budík za ${formatShortDuration(at - now)} ($time)" to TAB_BUDIK
    }
    val date = SimpleDateFormat("EEE d. MMM yyyy", Locale("cs")).format(java.util.Date(now))
    return date to TAB_HODINY
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

/**
 * The clock face is a [android.widget.Chronometer], not a plain TextView refreshed every
 * second from here: a Chronometer ticks on its own inside the launcher's process once
 * armed, so the seconds display is immune to this app's background-alarm throttling.
 *
 * Its base is set so elapsed-since-base equals milliseconds-since-midnight, i.e. it
 * literally counts up through today's wall-clock time. DateUtils' elapsed-time formatter
 * omits the hour digits below 1h and never zero-pads a single-digit hour, so the wrapping
 * `format` string is swapped once per hour boundary to paper over both cases.
 *
 * Text sizes (and whether the status line shows at all) are derived from the widget's
 * *current* placed size via [AppWidgetManager.getAppWidgetOptions] every time this redraws,
 * rather than being fixed - a widget dragged out to fill most of a home screen used to look
 * exactly the same as the smallest possible one, with lots of dead space either way.
 */
private fun buildRemoteViews(context: Context, widgetId: Int, now: Long): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.clock_widget_layout)

    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
    val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 64)
    val timeSizeSp = when {
        minWidthDp < 100 -> 20f
        minWidthDp < 150 -> 26f
        minWidthDp < 220 -> 32f
        minWidthDp < 300 -> 40f
        else -> 48f
    }
    val statusSizeSp = when {
        minWidthDp < 100 -> 9f
        minWidthDp < 150 -> 10f
        minWidthDp < 220 -> 11f
        else -> 13f
    }
    views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, timeSizeSp)
    views.setTextViewTextSize(R.id.widget_status, TypedValue.COMPLEX_UNIT_SP, statusSizeSp)
    // Below this height there's only room for one line without clipping/overlap - drop the
    // status line entirely rather than let it get cut off.
    views.setViewVisibility(R.id.widget_status, if (minHeightDp < 50) View.GONE else View.VISIBLE)

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
    val (statusText, tab) = nearestEvent(context, now)
    views.setTextViewText(R.id.widget_status, statusText)

    val tapIntent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra("tab", tab)
    val tapPi = PendingIntent.getActivity(
        context, 50_000 + tab, tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, tapPi)
    return views
}

/** Redraws every KucLab Clock widget instance immediately, bypassing AlarmManager entirely.
 * Called directly (via a Handler loop, not a scheduled alarm) by StopwatchService/TimerService
 * while they're alive - background apps' frequent `setExactAndAllowWhileIdle` calls get
 * silently throttled down to roughly hourly by Android's app-standby alarm quotas (the same
 * class of problem the Chronometer switch solved for the seconds display), so a live
 * countdown needs a process that's actually kept alive (a foreground service), not more
 * alarms. */
fun pushWidgetUpdateNow(context: Context) {
    val mgr = AppWidgetManager.getInstance(context)
    val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
    val now = System.currentTimeMillis()
    ids.forEach { id -> mgr.updateAppWidget(id, buildRemoteViews(context, id, now)) }
}

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, id) }
        scheduleNextTick(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // The user just resized this widget on their home screen - redraw it immediately
        // with text sized for the new dimensions instead of waiting for the next hourly tick.
        updateWidget(context, appWidgetId)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(hourlyTickPi(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK_ALL -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
                ids.forEach { id -> updateWidget(context, id) }
                scheduleNextTick(context)
            }
        }
    }

    private fun updateWidget(context: Context, widgetId: Int) {
        AppWidgetManager.getInstance(context)
            .updateAppWidget(widgetId, buildRemoteViews(context, widgetId, System.currentTimeMillis()))
    }

    private fun hourlyTickPi(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 40_000,
        Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TICK_ALL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** The only thing a fully-idle widget needs to catch on its own is an hour boundary
     * (chronometer format bucket) or a new day - anything more time-sensitive (a running
     * timer/stopwatch) is pushed live by [pushWidgetUpdateNow] from their own foreground
     * service instead, so this stays a single cheap alarm per hour regardless of how many
     * widgets exist or how long the app goes unopened. */
    private fun scheduleNextTick(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val pi = hourlyTickPi(context)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        }
    }

    companion object {
        const val ACTION_TICK_ALL = "dev.kuclab.clock.action.WIDGET_TICK_ALL"
    }
}
