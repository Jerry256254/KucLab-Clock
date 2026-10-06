package dev.kuclab.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AlarmScheduler {

    // Derived from a masked hash instead of `id * 2` so large/random ids can never overflow
    // Int and silently collide with a different alarm's request code (which previously could
    // cause one alarm's PendingIntent to be overwritten by another's, making the wrong alarm ring).
    private fun requestCode(alarm: Alarm, once: Boolean): Int {
        val h = (alarm.id xor (alarm.id ushr 32)).toInt() and 0x3FFFFFFF
        return h * 2 + if (once) 1 else 0
    }

    private fun pi(context: Context, alarm: Alarm, once: Boolean): PendingIntent {
        val requestCode = requestCode(alarm, once)
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra("id", alarm.id)
            .putExtra("once", once)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.enabled) return
        val at = nextTrigger(alarm) ?: return
        setExact(context, at, pi(context, alarm, once = false))
    }

    fun scheduleOnce(context: Context, alarm: Alarm, atMillis: Long) {
        setExact(context, atMillis, pi(context, alarm, once = true))
    }

    fun cancel(context: Context, alarm: Alarm) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pi(context, alarm, once = false))
        am.cancel(pi(context, alarm, once = true))
        WakeCheckScheduler.cancel(context, alarm.id)
    }

    private fun setExact(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000, pi)
        }
    }

    /** The single soonest-to-ring enabled alarm, if any - used by the widget to show
     * "next alarm in Xh Ym" when neither the timer nor the stopwatch is currently running. */
    fun nextUpcoming(context: Context): Pair<Alarm, Long>? =
        Alarms.load(context)
            .filter { it.enabled }
            .mapNotNull { a -> nextTrigger(a)?.let { a to it } }
            .minByOrNull { it.second }

    fun nextTrigger(alarm: Alarm, fromMillis: Long = System.currentTimeMillis()): Long? {
        val now = Calendar.getInstance().apply { timeInMillis = fromMillis }
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromMillis
        if (alarm.days.isEmpty()) {
            cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            cal.set(Calendar.MINUTE, alarm.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 1)
            return cal.timeInMillis
        }
        // Fourteen days covers a full extra repeat cycle when the nearest weekly
        // occurrence is deliberately skipped.
        for (i in 0..14) {
            val c = now.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, i)
            val ourDay = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            if (alarm.days.contains(ourDay)) {
                c.set(Calendar.HOUR_OF_DAY, alarm.hour)
                c.set(Calendar.MINUTE, alarm.minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (i == 0 && c.timeInMillis <= now.timeInMillis) continue
                val candidate = c.timeInMillis
                val skipped = alarm.skippedOccurrenceAt
                if (skipped != null && kotlin.math.abs(candidate - skipped) < 60_000L) continue
                return candidate
            }
        }
        return null
    }

    fun skipNext(alarm: Alarm): Alarm? {
        if (alarm.days.isEmpty() || !alarm.enabled) return null
        val occurrence = nextTrigger(alarm.copy(skippedOccurrenceAt = null)) ?: return null
        return alarm.copy(skippedOccurrenceAt = occurrence)
    }
}
