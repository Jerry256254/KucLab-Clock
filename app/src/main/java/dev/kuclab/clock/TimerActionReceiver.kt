package dev.kuclab.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "dev.kuclab.clock.action.TIMER_STOP"
        const val ACTION_EXTEND = "dev.kuclab.clock.action.TIMER_EXTEND"
        const val EXTEND_MS = 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                AlarmService.stop(context)
                TimerState.clear(context)
                WidgetRefresh.requestUpdate(context)
            }
            ACTION_EXTEND -> {
                AlarmService.stop(context)
                val at = System.currentTimeMillis() + EXTEND_MS
                TimerScheduler.schedule(context, at)
                TimerState.save(context, running = true, endAtWallClock = at, totalMs = EXTEND_MS)
                WidgetRefresh.requestUpdate(context)
            }
        }
    }
}
