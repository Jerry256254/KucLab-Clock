package dev.kuclab.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // The timer reached zero. Unlike an alarm this must NOT force its way onto the
        // screen over whatever the user is doing (see AlarmService.buildNotification's
        // isTimer branch and AlarmActivity's simplified isTimer path) - it just rings and
        // posts a persistent notification with Stop/+1 min actions.
        TimerState.save(context, running = false, endAtWallClock = 0L, totalMs = 0L)
        AlarmService.start(context, -1L, "Časovač", isTimer = true)
        WidgetRefresh.requestUpdate(context)
    }
}
