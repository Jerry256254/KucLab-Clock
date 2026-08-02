package dev.kuclab.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.StopwatchService
import dev.kuclab.clock.StopwatchState
import dev.kuclab.clock.WidgetRefresh
import kotlinx.coroutines.delay

private fun formatStopwatch(ms: Long): String {
    val mins = ms / 60000
    val secs = (ms % 60000) / 1000
    val cents = (ms % 1000) / 10
    return String.format("%02d:%02d.%02d", mins, secs, cents)
}

@Composable
fun StopwatchScreen() {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var running by rememberSaveable { mutableStateOf(false) }
    var startAt by rememberSaveable { mutableStateOf(0L) }
    var accumulated by rememberSaveable { mutableStateOf(0L) }
    var display by rememberSaveable { mutableStateOf(0L) }
    val laps = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) {
            display = accumulated + (SystemClock.elapsedRealtime() - startAt)
            delay(30)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.hairlineCard().padding(horizontal = 36.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        formatStopwatch(display),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Light,
                        color = OnDark
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (running) "MĚŘÍ SE…" else "STOPKY",
                        color = if (running) Accent else Muted,
                        letterSpacing = 4.sp,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(30.dp))

                // Every button gets an equal weight() share of the row's width instead of a
                // fixed dp size, so "Reset" (the longest label) always has room to fit on
                // one line no matter the device width or font scale.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            if (running) {
                                accumulated += SystemClock.elapsedRealtime() - startAt
                                running = false
                                StopwatchService.stop(ctx)
                                StopwatchState.save(ctx, running = false, startAtWallClock = 0L, accumulatedMs = accumulated)
                            } else {
                                startAt = SystemClock.elapsedRealtime()
                                running = true
                                val startWallClock = System.currentTimeMillis()
                                StopwatchService.start(ctx, startWallClock)
                                StopwatchState.save(ctx, running = true, startAtWallClock = startWallClock, accumulatedMs = accumulated)
                            }
                            WidgetRefresh.requestUpdate(ctx)
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1.2f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) ErrRed else Accent,
                            contentColor = if (running) Color.White else OnAccent
                        )
                    ) {
                        Text(if (running) "Pozastavit" else "Start", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            laps.add(display)
                        },
                        enabled = running,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
                    ) {
                        Text("Kolo", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            running = false
                            accumulated = 0
                            display = 0
                            laps.clear()
                            StopwatchService.stop(ctx)
                            StopwatchState.clear(ctx)
                            WidgetRefresh.requestUpdate(ctx)
                        },
                        enabled = display > 0 && !running,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)
                    ) {
                        Text("Reset", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        if (laps.isNotEmpty()) {
            Text(
                "KOLA (${laps.size})",
                color = Muted,
                letterSpacing = 3.sp,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Capped to roughly 5 rows so a long lap list scrolls internally instead of
            // pushing the Start/Kolo/Reset controls above it off the top of the screen.
            LazyColumn(
                modifier = Modifier.heightIn(max = 230.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(laps.asReversed(), key = { i, _ -> laps.size - i }) { i, lap ->
                    val lapNumber = laps.size - i
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hairlineCard()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Kolo $lapNumber",
                            color = Muted,
                            fontSize = 14.sp
                        )
                        Text(
                            formatStopwatch(lap),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = OnDark
                        )
                    }
                }
            }
        }
    }
}
