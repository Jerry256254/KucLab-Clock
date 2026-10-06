package dev.kuclab.clock

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        val once = intent.getBooleanExtra("once", false)
        val alarm = Alarms.load(context).firstOrNull { it.id == id } ?: return

        AlarmService.start(context, id, alarm.label, isTimer = false)
        launchAlarmActivity(context, id, alarm.label, isTimer = false)

        if (once) return

        if (alarm.days.isEmpty()) {
            Alarms.setEnabled(context, id, false)
            AlarmScheduler.cancel(context, alarm)
        } else if (alarm.enabled) {
            // A stored skip has served its purpose once a later occurrence actually fires.
            val nextAlarm = if (alarm.skippedOccurrenceAt != null) {
                alarm.copy(skippedOccurrenceAt = null).also { Alarms.upsert(context, it) }
            } else alarm
            AlarmScheduler.schedule(context, nextAlarm)
        }
    }
}

fun launchAlarmActivity(context: Context, id: Long, label: String, isTimer: Boolean) {
    val intent = Intent(context, AlarmActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("id", id)
        .putExtra("timer", isTimer)
        .putExtra("label", label)
    val pi = PendingIntent.getActivity(
        context, (id % 1_000_000).toInt() + 100, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    try {
        pi.send()
    } catch (_: PendingIntent.CanceledException) {
    }
}
