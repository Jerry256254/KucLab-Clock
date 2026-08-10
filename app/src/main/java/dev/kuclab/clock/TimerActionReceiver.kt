package dev.kuclab.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "dev.kuclab.clock.action.TIMER_STOP"
        const val ACTION_EXTEND = "dev.kuclab.clock.action.TIMER_EXTEND"
        // From the "Zrušit" action on the running-timer notification (TimerService) -
        // distinct from ACTION_STOP, which silences the *finished* timer's ringing.
        const val ACTION_CANCEL_RUNNING = "dev.kuclab.clock.action.TIMER_CANCEL_RUNNING"
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
                TimerService.start(context, at)
                WidgetRefresh.requestUpdate(context)
            }
            ACTION_CANCEL_RUNNING -> {
                TimerScheduler.cancel(context)
                TimerState.clear(context)
                TimerService.stop(context)
                WidgetRefresh.requestUpdate(context)
            }
        }
    }
}
