package dev.kuclab.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var totalMs by rememberSaveable { mutableStateOf(0L) }
    var remainingMs by rememberSaveable { mutableStateOf(0L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var alarmAt by rememberSaveable { mutableStateOf(0L) } // elapsedRealtime-based, for smooth local countdown

    fun start(ms: Long) {
        val at = System.currentTimeMillis() + ms
        TimerScheduler.schedule(ctx, at)
        TimerState.save(ctx, running = true, endAtWallClock = at, totalMs = ms)
        TimerService.start(ctx, at)
        WidgetRefresh.requestUpdate(ctx)
        alarmAt = SystemClock.elapsedRealtime() + ms
    }

    fun cancel() {
        TimerScheduler.cancel(ctx)
        TimerState.clear(ctx)
        TimerService.stop(ctx)
        WidgetRefresh.requestUpdate(ctx)
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

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val fraction = if (totalMs > 0) {
                    (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                    fraction, claudeSpring(), label = "timerRing"
                )

                // Canvas's onDraw lambda runs in the draw phase, not composition, so the
                // theme colors (backed by a CompositionLocal, which needs @Composable
                // context) are resolved here and captured as plain Color values.
                val trackColor = TrackBg
                val accentColor = Accent
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(240.dp)
                            .hairlineCard(shape = CircleShape, fill = Color.Transparent)
                    )
                    Canvas(Modifier.size(224.dp)) {
                        val stroke = 12.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedFraction,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        if (totalMs > 0) formatRemaining(remainingMs) else "--:--",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Light,
                        color = OnDark
                    )
                }

                Spacer(Modifier.height(28.dp))

                if (!running) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS_MIN.forEach { min ->
                            val selected = totalMs == min * 60_000L
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptics.tap()
                                    totalMs = min * 60_000L
                                    remainingMs = totalMs
                                },
                                label = { Text("${min} min", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CardBg,
                                    labelColor = Muted,
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = OnAccent
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HapticSlider(
                        value = (totalMs / 60_000f),
                        onValueChange = { min ->
                            totalMs = (min * 60_000f).toLong()
                            remainingMs = totalMs
                        },
                        valueRange = 0f..MAX_TIMER_MINUTES,
                        stepSize = 0.5f,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                    Spacer(Modifier.height(24.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(width = 160.dp, height = 56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = OnAccent,
                            disabledContainerColor = CardBg,
                            disabledContentColor = Muted
                        )
                    ) {
                        Text(if (running) "Pozastavit" else "Spustit", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            cancel()
                            running = false
                            totalMs = 0
                            remainingMs = 0
                        },
                        enabled = totalMs > 0,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(width = 120.dp, height = 56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
                    ) {
                        Text("Zrušit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
