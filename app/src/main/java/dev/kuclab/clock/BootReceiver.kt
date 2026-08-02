package dev.kuclab.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Alarms.load(context)
                    .filter { it.enabled }
                    .forEach { AlarmScheduler.schedule(context, it) }
                WidgetRefresh.requestUpdate(context)
            }
        }
    }
}
