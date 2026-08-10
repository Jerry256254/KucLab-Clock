package dev.kuclab.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * A tiny foreground service whose only job is to keep a `CATEGORY_STOPWATCH`,
 * `setUsesChronometer(true)` notification alive while the stopwatch is running - this is
 * the standard Android mechanism (the same one Google's own Clock app uses) for a running
 * stopwatch to show up on the lock screen / always-on display and sort near the top of the
 * notification shade. It carries no logic of its own: StopwatchScreen owns the actual
 * elapsed-time state and just tells this service when to start/stop.
 *
 * While alive it also pushes the home-screen widget's "nearest event" line directly via
 * [pushWidgetUpdateNow] on a plain in-process Handler loop, instead of the widget scheduling
 * its own frequent AlarmManager alarms - Android throttles a background app's own frequent
 * `setExactAndAllowWhileIdle` calls down to roughly hourly, which is exactly the staleness
 * that was reported ("aktivní tooly se obnovují jen asi každou hodinu"). A running foreground
 * service isn't subject to that throttling, so ticking from here is reliable.
 */
class StopwatchService : Service() {

    companion object {
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val NOTIF_ID = 2001
        private const val WIDGET_PUSH_INTERVAL_MS = 15_000L
        const val ACTION_START = "dev.kuclab.clock.action.STOPWATCH_START"
        const val ACTION_STOP = "dev.kuclab.clock.action.STOPWATCH_STOP"
        private const val EXTRA_START_AT = "startAt"

        fun start(context: Context, startAtWallClock: Long) {
            val intent = Intent(context, StopwatchService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_START_AT, startAtWallClock)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, StopwatchService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val widgetPushLoop = object : Runnable {
        override fun run() {
            pushWidgetUpdateNow(this@StopwatchService)
            handler.postDelayed(this, WIDGET_PUSH_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Stopky", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Živý stav běžících stopek"
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
                val startAt = intent.getLongExtra(EXTRA_START_AT, System.currentTimeMillis())
                val notification = Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_alarm)
                    .setContentTitle("Stopky běží")
                    .setCategory(Notification.CATEGORY_STOPWATCH)
                    .setUsesChronometer(true)
                    .setWhen(startAt)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            this, 0,
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra("tab", 2),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    .build()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIF_ID, notification)
                }
                handler.removeCallbacks(widgetPushLoop)
                handler.post(widgetPushLoop)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(widgetPushLoop)
        pushWidgetUpdateNow(this)
        super.onDestroy()
    }
}
