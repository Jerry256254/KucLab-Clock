package dev.kuclab.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Mirrors [StopwatchService]: a foreground service alive only while the timer is actively
 * counting down, showing a low-priority "Časovač běží" notification with the remaining time
 * and a Cancel action (the same pattern Google's own Clock app uses for a running timer),
 * and - the actual point of it - pushing the home-screen widget's status line directly via
 * [pushWidgetUpdateNow] on an in-process Handler loop instead of relying on the widget's own
 * AlarmManager alarms, which Android throttles down to roughly hourly for a backgrounded app.
 */
class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "timer_running_channel"
        private const val NOTIF_ID = 2002
        private const val WIDGET_PUSH_INTERVAL_MS = 15_000L
        const val ACTION_START = "dev.kuclab.clock.action.TIMER_RUN_START"
        const val ACTION_STOP = "dev.kuclab.clock.action.TIMER_RUN_STOP"
        private const val EXTRA_END_AT = "endAt"

        fun start(context: Context, endAtWallClock: Long) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_END_AT, endAtWallClock)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, TimerService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private var endAt = 0L
    private var lastWidgetPush = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val tickLoop = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val remaining = (endAt - now).coerceAtLeast(0)
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(remaining))
            if (now - lastWidgetPush >= WIDGET_PUSH_INTERVAL_MS) {
                pushWidgetUpdateNow(this@TimerService)
                lastWidgetPush = now
            }
            if (remaining > 0) {
                handler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Časovač (běží)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zbývající čas běžícího časovače"
                setShowBadge(false)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                endAt = intent.getLongExtra(EXTRA_END_AT, System.currentTimeMillis())
                lastWidgetPush = 0L
                val remaining = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIF_ID, buildNotification(remaining), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, buildNotification(remaining))
                }
                handler.removeCallbacks(tickLoop)
                handler.post(tickLoop)
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(remainingMs: Long): Notification {
        val totalSec = (remainingMs + 999) / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        val cancelPi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, TimerActionReceiver::class.java).setAction(TimerActionReceiver.ACTION_CANCEL_RUNNING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("tab", 3),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Časovač běží")
            .setContentText("Zbývá %02d:%02d".format(m, s))
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPi)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_alarm), "Zrušit", cancelPi
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickLoop)
        pushWidgetUpdateNow(this)
        super.onDestroy()
    }
}
