package dev.kuclab.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Single source of truth for the timer's countdown alarm. Everything that can (re)arm or
 * disarm the timer - the Timer screen, the "+1 min" notification action, a future snooze -
 * goes through this one request code, so two different call sites can never end up with two
 * independent alarms both racing to ring (which is what happened before, when TimerScreen
 * and AlarmActivity's old snooze() used different PendingIntent request codes for the same
 * receiver).
 */
object TimerScheduler {
    private const val REQUEST_CODE = 700_001

    private fun pi(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, TimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(context: Context, atWallClockMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val p = pi(context)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWallClockMillis, p)
        } catch (_: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, atWallClockMillis, 60_000, p)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pi(context))
    }
}
