package dev.kuclab.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.TimerScheduler
import dev.kuclab.clock.TimerService
import dev.kuclab.clock.TimerState
import dev.kuclab.clock.WidgetRefresh
import kotlinx.coroutines.delay
import kotlin.math.max

private const val MAX_TIMER_MINUTES = 60f
private val PRESETS_MIN = listOf(1, 5, 10, 15, 30)

private fun formatRemaining(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var totalMs by rememberSaveable { mutableStateOf(0L) }
    var remainingMs by rememberSaveable { mutableStateOf(0L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var alarmAt by rememberSaveable { mutableStateOf(0L) }

    fun start(ms: Long) {
        val wallClock = System.currentTimeMillis() + ms
        TimerScheduler.schedule(context, wallClock)
        TimerState.save(context, true, wallClock, ms)
        TimerService.start(context, wallClock)
        WidgetRefresh.requestUpdate(context)
        alarmAt = SystemClock.elapsedRealtime() + ms
    }

    fun cancel() {
        TimerScheduler.cancel(context)
        TimerState.clear(context)
        TimerService.stop(context)
        WidgetRefresh.requestUpdate(context)
    }

    LaunchedEffect(running) {
        while (running) {
            remainingMs = max(0L, alarmAt - SystemClock.elapsedRealtime())
            if (remainingMs <= 0) {
                running = false
                totalMs = 0
                remainingMs = 0
            }
            delay(50)
        }
    }

    val fraction = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Text("Časovač", color = OnDark, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (totalMs > 0) formatRemaining(remainingMs) else "00:00",
                color = if (totalMs > 0) OnDark else Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                if (running) "Zbývá" else if (totalMs > 0) "Připraveno" else "Vyberte délku",
                color = if (running) Accent else Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Accent,
                trackColor = TrackBg
            )
        }

        if (!running) {
            Text("Rychlá volba", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PRESETS_MIN.forEach { minutes ->
                    val selected = totalMs == minutes * 60_000L
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Accent else CardBg)
                            .clickable {
                                haptics.tap()
                                totalMs = minutes * 60_000L
                                remainingMs = totalMs
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (minutes == 1) "1 m" else "$minutes m",
                            color = if (selected) OnAccent else OnDark,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Délka", color = OnDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${(totalMs / 60_000f).let { if (it % 1f == 0f) it.toInt().toString() else String.format("%.1f", it) }} min", color = Accent, fontSize = 14.sp)
            }
            HapticSlider(
                value = totalMs / 60_000f,
                onValueChange = { minutes ->
                    totalMs = (minutes * 60_000f).toLong()
                    remainingMs = totalMs
                },
                valueRange = 0f..MAX_TIMER_MINUTES,
                stepSize = .5f,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    haptics.confirm()
                    if (running) {
                        cancel()
                        running = false
                    } else if (totalMs > 0) {
                        start(if (remainingMs > 0) remainingMs else totalMs)
                        running = true
                    }
                },
                enabled = totalMs > 0 || running,
                modifier = Modifier.weight(1.5f).height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = CardBg,
                    disabledContentColor = Muted
                )
            ) { Text(if (running) "Pozastavit" else "Spustit", fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick = {
                    haptics.tap()
                    cancel()
                    running = false
                    totalMs = 0
                    remainingMs = 0
                },
                enabled = totalMs > 0,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                border = BorderStroke(1.dp, Hairline)
            ) { Text("Vynulovat") }
        }
        Spacer(Modifier.height(14.dp))
    }
}
