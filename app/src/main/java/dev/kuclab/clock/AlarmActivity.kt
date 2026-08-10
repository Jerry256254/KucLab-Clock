package dev.kuclab.clock

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.kuclab.clock.ui.Accent
import dev.kuclab.clock.ui.OnAccent
import dev.kuclab.clock.ui.KucLabTheme
import dev.kuclab.clock.ui.ErrRed
import dev.kuclab.clock.ui.Ink
import dev.kuclab.clock.ui.Muted
import dev.kuclab.clock.ui.OnDark
import dev.kuclab.clock.ui.TrackBg
import dev.kuclab.clock.ui.confirm
import dev.kuclab.clock.ui.hairlineCard
import kotlin.math.sqrt
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmActivity : ComponentActivity() {

    private var isTimerFlag = false
    // True only while this activity itself opened an in-app system dialog (the
    // step-counter permission prompt) - the watchdog below must not fight that dialog
    // for the foreground.
    private var awaitingSystemDialog = false
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

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
        volumeControlStream = AudioManager.STREAM_ALARM
        applyImmersiveMode()

        val id = intent.getLongExtra("id", -1L)
        val isTimer = intent.getBooleanExtra("timer", false)
        val alarm = Alarms.load(this).firstOrNull { it.id == id }
        val label = intent.getStringExtra("label")
            ?: alarm?.label
            ?: if (isTimer) "Časovač" else "Budík"

        if (!AlarmService.isRinging) {
            AlarmService.start(this, id, label, isTimer)
        }

        renderChallenge(id, label, isTimer, alarm)
    }

    /** Hides the status/navigation bars (swipe-to-reveal only) so the shade and gesture
     * nav are at least out of the way by default while a real alarm rings. This is a
     * cooperative, public-API measure - a regular (non device-owner) app fundamentally
     * cannot block the OS's own escape hatches like the power button or emergency dialer,
     * and shouldn't try to. */
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        stopWatchdog()
        // Screen-pin the task so Home/Recents can't leave the ringing screen behind - but
        // ONLY for a real alarm. A timer must never lock the task: it's meant to be
        // dismissible like any other notification-driven screen (see TimerReceiver).
        if (!isTimerFlag) {
            try {
                startLockTask()
            } catch (_: Exception) {
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // The actual backstop against "just leave the ringing screen": whenever this
        // activity loses the foreground for ANY reason (Home, Recents, opening another app
        // from the notification shade, a phone call, ...) while a real alarm is still
        // ringing and we didn't finish() it ourselves, keep trying to bring it back - not
        // just once (onUserLeaveHint alone missed cases like Recents/notification-shade
        // launches on some Android versions/OEMs), but repeatedly every ~400ms, since a
        // single relaunch attempt can itself race with whatever just took focus.
        if (!isTimerFlag && AlarmService.isRinging && !isFinishing && !awaitingSystemDialog) {
            startWatchdog()
        }
    }

    private fun startWatchdog() {
        if (watchdogRunnable != null) return
        val r = object : Runnable {
            override fun run() {
                if (isTimerFlag || !AlarmService.isRinging || isFinishing || awaitingSystemDialog) {
                    watchdogRunnable = null
                    return
                }
                try {
                    startActivity(
                        Intent(this@AlarmActivity, AlarmActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
                watchdogHandler.postDelayed(this, 400)
            }
        }
        watchdogRunnable = r
        watchdogHandler.postDelayed(r, 300)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A different alarm/timer took over the ringing session (see AlarmService's
        // hand-off logic) - rebuild the screen so it matches what's actually ringing
        // instead of leaving stale problems for an alarm that's no longer being serviced.
        val id = intent.getLongExtra("id", -1L)
        val isTimer = intent.getBooleanExtra("timer", false)
        val alarm = Alarms.load(this).firstOrNull { it.id == id }
        val label = intent.getStringExtra("label")
            ?: alarm?.label
            ?: if (isTimer) "Časovač" else "Budík"
        renderChallenge(id, label, isTimer, alarm)
    }

    private fun renderChallenge(id: Long, label: String, isTimer: Boolean, alarm: Alarm?) {
        isTimerFlag = isTimer
        setContent {
            KucLabTheme {
                ChallengeScreen(
                    alarmLabel = label,
                    isTimer = isTimer,
                    mathRequired = !isTimer && (alarm?.math ?: true),
                    mathCount = alarm?.mathCount ?: 3,
                    stepsRequired = !isTimer && (alarm?.stepsRequired ?: false),
                    stepsCount = alarm?.stepsCount ?: 20,
                    shakeRequired = !isTimer && (alarm?.shakeRequired ?: false),
                    shakeCount = alarm?.shakeCount ?: 15,
                    snoozeAllowed = isTimer || (alarm?.snooze ?: true),
                    snoozeLabel = if (isTimer) "Prodloužit o 1 min" else "Odložit o ${alarm?.snoozeMinutes ?: 5} min",
                    onSystemDialogStart = { awaitingSystemDialog = true },
                    onSystemDialogEnd = { awaitingSystemDialog = false },
                    onDismiss = {
                        stopWatchdog()
                        stopLockTaskSafely()
                        AlarmService.stop(this)
                        if (isTimer) TimerState.clear(this)
                        WidgetRefresh.requestUpdate(this)
                        finish()
                    },
                    onSnooze = {
                        stopWatchdog()
                        snooze(id, isTimer)
                        stopLockTaskSafely()
                        AlarmService.stop(this)
                        WidgetRefresh.requestUpdate(this)
                        finish()
                    }
                )
            }
        }
    }

    private fun stopLockTaskSafely() {
        try {
            stopLockTask()
        } catch (_: Exception) {
        }
    }

    private fun snooze(id: Long, isTimer: Boolean) {
        if (isTimer) {
            val at = System.currentTimeMillis() + TimerActionReceiver.EXTEND_MS
            TimerScheduler.schedule(this, at)
            TimerState.save(this, running = true, endAtWallClock = at, totalMs = TimerActionReceiver.EXTEND_MS)
            TimerService.start(this, at)
        } else {
            val alarm = Alarms.load(this).firstOrNull { it.id == id } ?: return
            val at = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            AlarmScheduler.scheduleOnce(this, alarm, at)
        }
    }

    override fun onDestroy() {
        stopWatchdog()
        super.onDestroy()
    }
}

data class Problem(val a: Int, val b: Int, val op: Char, val answer: Int)

private fun generateProblems(count: Int): List<Problem> {
    val ops = listOf('+', '-', '×')
    return List(count) {
        val op = ops[Random.nextInt(ops.size)]
        var a = 2 + Random.nextInt(11)
        var b = 2 + Random.nextInt(11)
        if (op == '-' && a < b) {
            val t = a; a = b; b = t
        }
        val answer = when (op) {
            '+' -> a + b
            '-' -> a - b
            else -> a * b
        }
        Problem(a, b, op, answer)
    }
}

private fun hasActivityRecognitionPermission(ctx: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** Live count of TYPE_STEP_DETECTOR events since this screen appeared, capped at [target].
 * Returns null if the device has no step sensor at all (hardware limitation, not something
 * the user can fix - that case is treated as satisfied automatically so the alarm can never
 * become permanently undismissable). */
@Composable
private fun rememberStepProgress(target: Int, permissionGranted: Boolean): Int? {
    val ctx = LocalContext.current
    var count by remember { mutableStateOf(0) }
    var sensorMissing by remember { mutableStateOf(false) }
    DisposableEffect(target, permissionGranted) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            sensorMissing = true
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (count < target) {
                    count++
                    vibrateOnce(ctx, 25)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (permissionGranted) {
            try {
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            } catch (_: SecurityException) {
            }
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return if (sensorMissing) null else count
}

/** Live count of distinct shake gestures (accelerometer force above a threshold, debounced),
 * capped at [target]. Null if there's no accelerometer at all. */
@Composable
private fun rememberShakeProgress(target: Int): Int? {
    val ctx = LocalContext.current
    var count by remember { mutableStateOf(0) }
    var sensorMissing by remember { mutableStateOf(false) }
    DisposableEffect(target) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            sensorMissing = true
            return@DisposableEffect onDispose {}
        }
        var lastShakeAt = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
                if (gForce > 2.2f) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastShakeAt > 350) {
                        lastShakeAt = now
                        if (count < target) {
                            count++
                            vibrateOnce(ctx, 25)
                        }
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return if (sensorMissing) null else count
}

@Composable
private fun RequirementCard(title: String, progress: Int, target: Int, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .hairlineCard()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text("$progress / $target", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = Accent,
            trackColor = TrackBg
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
fun ChallengeScreen(
    alarmLabel: String,
    isTimer: Boolean,
    mathRequired: Boolean,
    mathCount: Int,
    stepsRequired: Boolean,
    stepsCount: Int,
    shakeRequired: Boolean,
    shakeCount: Int,
    snoozeAllowed: Boolean,
    snoozeLabel: String,
    onSystemDialogStart: () -> Unit,
    onSystemDialogEnd: () -> Unit,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var timeNow by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val c = Calendar.getInstance()
            timeNow = String.format(
                "%02d:%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
            )
            kotlinx.coroutines.delay(200)
        }
    }

    val problemCount = mathCount.coerceIn(1, 5)
    val problems = remember(problemCount) { generateProblems(problemCount) }
    var index by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(0) }
    val mathDone = !mathRequired || solved >= problemCount

    var hasActivityRecognition by remember { mutableStateOf(hasActivityRecognitionPermission(ctx)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasActivityRecognition = granted
        onSystemDialogEnd()
    }
    val stepsTarget = stepsCount.coerceAtLeast(1)
    val stepsProgress = if (stepsRequired) rememberStepProgress(stepsTarget, hasActivityRecognition) else null
    val stepsDone = !stepsRequired || stepsProgress == null || stepsProgress >= stepsTarget

    val shakeTarget = shakeCount.coerceAtLeast(1)
    val shakeProgress = if (shakeRequired) rememberShakeProgress(shakeTarget) else null
    val shakeDone = !shakeRequired || shakeProgress == null || shakeProgress >= shakeTarget

    val anyRequirement = mathRequired || stepsRequired || shakeRequired
    val allDone = mathDone && stepsDone && shakeDone

    LaunchedEffect(allDone) {
        if (anyRequirement && allDone) {
            haptics.confirm()
            kotlinx.coroutines.delay(600)
            onDismiss()
        }
    }
    // A real alarm can only be silenced by clearing every configured requirement (or
    // snoozing, if that alarm allows it) - back is blocked, startLockTask()/the relaunch
    // watchdog in AlarmActivity back that up. A timer has no such requirement: back just
    // leaves the (still-ringing, still-notified) session, same as dismissing any other
    // notification-driven screen.
    BackHandler(enabled = !isTimer) { }

    fun check() {
        if (index >= problems.size) return
        val p = problems[index]
        val answer = input.trim().toIntOrNull() ?: return
        if (answer == p.answer) {
            solved++
            index++
            input = ""
            wrong = false
            vibrateOnce(ctx, 80)
        } else {
            wrong = true
            input = ""
            vibrateOnce(ctx, 250)
        }
    }

    val today = remember {
        SimpleDateFormat("EEEE d. MMMM", Locale("cs")).format(Calendar.getInstance().time)
    }

    val breathing = rememberInfiniteTransition(label = "breathing")
    val pulse by breathing.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = pulse))
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    (if (isTimer) "ČASOVAČ DOBĚHL" else "BUDÍK").uppercase(Locale("cs")),
                    color = Muted,
                    letterSpacing = 4.sp,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(alarmLabel, color = OnDark, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(today.replaceFirstChar { it.uppercase(Locale("cs")) }, color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    timeNow,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    color = OnDark
                )
                Spacer(Modifier.height(28.dp))

                if (anyRequirement) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (mathRequired) {
                            Column(
                                Modifier.fillMaxWidth().hairlineCard().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Pro vypnutí vyřešte příklady", color = Muted, fontSize = 14.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "$solved / ${problems.size}",
                                    color = Accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(14.dp))
                                if (index < problems.size) {
                                    val p = problems[index]
                                    Text(
                                        "${p.a} ${p.op} ${p.b} = ?",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnDark
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = input,
                                            onValueChange = { v ->
                                                input = v.filter { it.isDigit() || it == '-' }.take(5)
                                                wrong = false
                                            },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(onDone = { check() }),
                                            singleLine = true,
                                            isError = wrong,
                                            modifier = Modifier.width(150.dp)
                                        )
                                        Button(
                                            onClick = { check() },
                                            shape = MaterialTheme.shapes.small,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Accent,
                                                contentColor = OnAccent
                                            )
                                        ) {
                                            Text("OK", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (wrong) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Špatně — zkuste znovu", color = ErrRed, fontSize = 13.sp)
                                    }
                                } else {
                                    Text("Hotovo!", color = OnDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (stepsRequired) {
                            if (!hasActivityRecognition) {
                                Column(
                                    Modifier.fillMaxWidth().hairlineCard().padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Pro krokový úkol povolte rozpoznávání aktivity",
                                        color = Muted,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            onSystemDialogStart()
                                            permissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                                        },
                                        shape = MaterialTheme.shapes.small,
                                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                                    ) { Text("Povolit", fontWeight = FontWeight.Bold) }
                                }
                            } else if (stepsProgress != null) {
                                RequirementCard("Ujděte pár kroků, ať doopravdy vstanete", stepsProgress, stepsTarget) {
                                    Text("Kroky se počítají automaticky, choďte s telefonem v ruce", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }

                        if (shakeRequired && shakeProgress != null) {
                            RequirementCard("Zatřeste telefonem", shakeProgress, shakeTarget) {
                                Text("Pořádně zamávejte telefonem ze strany na stranu", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onDismiss()
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = OnAccent
                        )
                    ) {
                        Text(if (isTimer) "Ukončit" else "Vypnout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (snoozeAllowed) {
                    Spacer(Modifier.height(22.dp))
                    OutlinedButton(
                        onClick = {
                            haptics.confirm()
                            onSnooze()
                        },
                        shape = MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) {
                        Text(snoozeLabel)
                    }
                }
                if (anyRequirement) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Budík lze vypnout jen po splnění všech úkolů výše",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
