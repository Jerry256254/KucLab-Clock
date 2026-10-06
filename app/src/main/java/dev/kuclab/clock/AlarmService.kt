package dev.kuclab.clock

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "alarm_channel"
        const val ACTION_START = "dev.kuclab.clock.action.ALARM_START"
        const val ACTION_STOP = "dev.kuclab.clock.action.ALARM_STOP"

        @Volatile
        var isRinging = false
            private set

        fun start(context: Context, id: Long, label: String, isTimer: Boolean) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra("id", id)
                .putExtra("label", label)
                .putExtra("timer", isTimer)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Oprávnění pro FGS blokuje start z pozadí – spustí se z AlarmActivity
            }
        }

        fun stop(context: Context) {
            isRinging = false
            try {
                context.stopService(Intent(context, AlarmService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var beepTask: Runnable? = null
    private var notifId = 1001
    private var currentId: Long = -1L
    private var currentIsTimer: Boolean = false
    private var previousAlarmVolume: Int? = null
    private var fadeInSeconds: Int = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRing()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val id = intent.getLongExtra("id", -1L)
                val label = intent.getStringExtra("label") ?: "Budík"
                val isTimer = intent.getBooleanExtra("timer", false)
                if (isRinging && id == currentId) {
                    // Duplicate trigger for the alarm that's already ringing (e.g. boot
                    // reschedule racing the original broadcast) - ignore, don't restart
                    // the player/vibrator or it would overlap with itself.
                    return START_NOT_STICKY
                }
                if (isRinging) {
                    // A different alarm/timer fired while one was still ringing: hand off
                    // cleanly instead of layering a second player/vibrator on top.
                    stopRing()
                }
                currentId = id
                currentIsTimer = isTimer
                prepareAlarmAudio()
                notifId = 1000 + (id % 100_000).toInt()
                isRinging = true
                startForegroundCompat(notifId, buildNotification(label, id, isTimer))
                startRing()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, notification)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "Budík", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Zvonění budíku a časovače"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(label: String, id: Long, isTimer: Boolean): Notification {
        val full = PendingIntent.getActivity(
            this, notifId,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("id", id)
                .putExtra("timer", isTimer)
                .putExtra("label", label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label.ifBlank { if (isTimer) "Časovač" else "Budík" })
            .setCategory(Notification.CATEGORY_ALARM)
            // Only actually forces the screen when the device is locked; when unlocked it's
            // just a high-priority heads-up, which is exactly what a finished timer wants -
            // never a hard takeover of whatever the user is doing (see TimerReceiver).
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .setAutoCancel(false)

        if (isTimer) {
            builder.setContentText("Časovač doběhl")
            builder.addAction(timerAction("Ukončit", TimerActionReceiver.ACTION_STOP))
            builder.addAction(timerAction("+1 min", TimerActionReceiver.ACTION_EXTEND))
        } else {
            builder.setContentText("Zvoní budík — probuďte se a vypněte ho")
        }
        return builder.build()
    }

    private fun timerAction(title: String, action: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(this, TimerActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_alarm),
            title, pi
        ).build()
    }

    private fun resolveRingtoneUri(): android.net.Uri? {
        val custom = if (!currentIsTimer) {
            Alarms.load(this).firstOrNull { it.id == currentId }?.ringtoneUri
        } else null
        if (custom != null) {
            try {
                return android.net.Uri.parse(custom)
            } catch (_: Exception) {
            }
        }
        return RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    private fun prepareAlarmAudio() {
        fadeInSeconds = 0
        if (currentIsTimer) return
        val alarm = Alarms.load(this).firstOrNull { it.id == currentId } ?: return
        fadeInSeconds = alarm.fadeInSeconds.coerceIn(0, 60)
        try {
            val audio = getSystemService(AudioManager::class.java)
            previousAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = kotlin.math.round(max * alarm.volumePercent.coerceIn(10, 100) / 100f)
                .toInt()
                .coerceIn(1, max.coerceAtLeast(1))
            audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        } catch (_: Exception) {
            previousAlarmVolume = null
        }
    }

    private fun startRing() {
        val uri = resolveRingtoneUri()
        if (uri != null) {
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    setOnPreparedListener { it.start() }
                    prepare()
                }
            } catch (_: Exception) {
                player = null
            }
        }

        if (player == null) {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            beepTask = object : Runnable {
                override fun run() {
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 1500)
                    handler.postDelayed(this, 2000)
                }
            }
            handler.post(beepTask!!)
        } else if (fadeInSeconds <= 0) {
            player?.setVolume(1f, 1f)
        } else {
            player?.let { p ->
                handler.post(object : Runnable {
                    var step = 0
                    override fun run() {
                        step++
                        val fraction = step / 20f
                        p.setVolume(fraction, fraction)
                        if (step < 20) {
                            handler.postDelayed(this, (fadeInSeconds * 1000L / 20L).coerceAtLeast(100L))
                        }
                    }
                })
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
    }

    private fun stopRing() {
        handler.removeCallbacksAndMessages(null)
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        tone?.release()
        tone = null
        beepTask = null
        vibrator?.cancel()
        vibrator = null
        previousAlarmVolume?.let { previous ->
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
            } catch (_: Exception) {
            }
        }
        previousAlarmVolume = null
        fadeInSeconds = 0
        try {
            getSystemService(NotificationManager::class.java).cancel(notifId)
        } catch (_: Exception) {
        }
        isRinging = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // The user swiped the app card away in Recents while a real alarm was still
        // ringing - previously this could silence it entirely with no challenge solved.
        // android:stopWithTask="false" on this service (see AndroidManifest.xml) already
        // keeps the ringing sound/vibration alive through that; this puts the ringing
        // screen itself back too, since the task that was hosting it is now gone. Timers
        // are deliberately excluded, matching every other place in this app where a timer
        // is allowed to be dismissed gently instead of forced back onto the screen.
        if (isRinging && !currentIsTimer) {
            try {
                startActivity(
                    Intent(this, AlarmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("id", currentId)
                        .putExtra("timer", false)
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        stopRing()
        super.onDestroy()
    }
}

fun vibrateOnce(context: Context, ms: Long) {
    try {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {
    }
}

fun hasNotificationPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}
