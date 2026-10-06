package dev.kuclab.clock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.ui.Accent
import dev.kuclab.clock.ui.KucLabTheme
import dev.kuclab.clock.ui.Muted
import dev.kuclab.clock.ui.OnAccent
import dev.kuclab.clock.ui.OnDark
import dev.kuclab.clock.ui.WakeSceneBackground
import dev.kuclab.clock.ui.glassCard

object WakeCheckScheduler {
    private const val ACTION_PROMPT = "dev.kuclab.clock.action.WAKE_CHECK_PROMPT"
    private const val ACTION_ESCALATE = "dev.kuclab.clock.action.WAKE_CHECK_ESCALATE"
    const val CHANNEL_ID = "wake_check_channel"
    private const val NOTIFICATION_BASE = 410_000

    private fun requestCode(id: Long, escalation: Boolean): Int {
        val hash = (id xor (id ushr 32)).toInt() and 0x0FFFFFFF
        return hash * 2 + if (escalation) 1 else 0
    }

    private fun pendingIntent(context: Context, id: Long, escalation: Boolean): PendingIntent {
        val intent = Intent(context, WakeCheckReceiver::class.java)
            .setAction(if (escalation) ACTION_ESCALATE else ACTION_PROMPT)
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            context,
            requestCode(id, escalation),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.wakeCheckEnabled) return
        cancel(context, alarm.id)
        val at = System.currentTimeMillis() + alarm.wakeCheckDelayMinutes.coerceIn(1, 15) * 60_000L
        setExact(context, at, pendingIntent(context, alarm.id, escalation = false))
    }

    fun scheduleEscalation(context: Context, alarm: Alarm) {
        val at = System.currentTimeMillis() + alarm.wakeCheckTimeoutMinutes.coerceIn(1, 5) * 60_000L
        setExact(context, at, pendingIntent(context, alarm.id, escalation = true))
    }

    fun acknowledge(context: Context, id: Long) = cancel(context, id)

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pendingIntent(context, id, escalation = false))
        manager.cancel(pendingIntent(context, id, escalation = true))
        context.getSystemService(NotificationManager::class.java)
            .cancel(NOTIFICATION_BASE + (id % 10_000).toInt())
    }

    private fun setExact(context: Context, at: Long, intent: PendingIntent) {
        val manager = context.getSystemService(AlarmManager::class.java)
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } catch (_: SecurityException) {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, 30_000L, intent)
        }
    }

    internal fun isPrompt(action: String?): Boolean = action == ACTION_PROMPT
    internal fun isEscalation(action: String?): Boolean = action == ACTION_ESCALATE
    internal fun notificationId(id: Long): Int = NOTIFICATION_BASE + (id % 10_000).toInt()
}

class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val alarm = Alarms.load(context).firstOrNull { it.id == id } ?: return

        when {
            WakeCheckScheduler.isPrompt(intent.action) -> {
                if (!alarm.wakeCheckEnabled) return
                WakeCheckScheduler.scheduleEscalation(context, alarm)
                showPrompt(context, alarm)
            }
            WakeCheckScheduler.isEscalation(intent.action) -> {
                context.getSystemService(NotificationManager::class.java)
                    .cancel(WakeCheckScheduler.notificationId(id))
                AlarmService.start(context, alarm.id, alarm.label, isTimer = false)
                launchAlarmActivity(context, alarm.id, alarm.label, isTimer = false)
            }
        }
    }

    private fun showPrompt(context: Context, alarm: Alarm) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                WakeCheckScheduler.CHANNEL_ID,
                "Kontrola probuzení",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ověří, že jste po vypnutí budíku opravdu vzhůru"
                enableVibration(true)
                setShowBadge(false)
            }
        )

        val fullScreen = PendingIntent.getActivity(
            context,
            WakeCheckScheduler.notificationId(alarm.id),
            Intent(context, WakeCheckActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("id", alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, WakeCheckScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Jste opravdu vzhůru?")
            .setContentText("Potvrďte to, jinak se budík znovu spustí.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        manager.notify(WakeCheckScheduler.notificationId(alarm.id), notification)
        try {
            fullScreen.send()
        } catch (_: PendingIntent.CanceledException) {
        }
    }
}

class WakeCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 27) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getLongExtra("id", -1L)
        val alarm = Alarms.load(this).firstOrNull { it.id == id }
        if (alarm == null) {
            finish()
            return
        }
        setContent {
            KucLabTheme {
                WakeCheckScreen(alarm) {
                    WakeCheckScheduler.acknowledge(this, alarm.id)
                    finishAndRemoveTask()
                }
            }
        }
    }
}

@Composable
private fun WakeCheckScreen(alarm: Alarm, onAwake: () -> Unit) {
    BackHandler { }
    WakeSceneBackground(WakeScene.fromId(alarm.wakeScene)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glassCard()
                    .padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Kontrola probuzení", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Text("Jsi opravdu vzhůru?", color = OnDark, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Když nepotvrdíš během ${alarm.wakeCheckTimeoutMinutes} min, budík se znovu rozjede.",
                    color = Muted,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = onAwake,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                ) {
                    Text("Jsem vzhůru", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    }
}
