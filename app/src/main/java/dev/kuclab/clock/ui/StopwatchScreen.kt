package dev.kuclab.clock.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val context = LocalContext.current
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

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Text("Stopky", color = OnDark, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                formatStopwatch(display),
                fontFamily = FontFamily.Monospace,
                fontSize = 55.sp,
                fontWeight = FontWeight.Light,
                color = OnDark
            )
            Text(
                if (running) "Měření běží" else if (display > 0) "Pozastaveno" else "Připraveno",
                color = if (running) Accent else Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (laps.isNotEmpty()) {
            Text("Kola", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(
                modifier = Modifier.weight(.75f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(laps.asReversed(), key = { index, _ -> laps.size - index }) { index, lap ->
                    val number = laps.size - index
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kolo $number", color = Muted, fontSize = 14.sp)
                        Text(formatStopwatch(lap), color = OnDark, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    haptics.confirm()
                    if (running) {
                        accumulated += SystemClock.elapsedRealtime() - startAt
                        running = false
                        StopwatchService.stop(context)
                        StopwatchState.save(context, false, 0L, accumulated)
                    } else {
                        startAt = SystemClock.elapsedRealtime()
                        running = true
                        val wallClock = System.currentTimeMillis()
                        StopwatchService.start(context, wallClock)
                        StopwatchState.save(context, true, wallClock, accumulated)
                    }
                    WidgetRefresh.requestUpdate(context)
                },
                modifier = Modifier.weight(1.35f).height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) ErrRed else Accent,
                    contentColor = if (running) Color.White else OnAccent
                )
            ) { Text(if (running) "Pozastavit" else "Start", fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick = { haptics.tap(); laps.add(display) },
                enabled = running,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnDark),
                border = BorderStroke(1.dp, Hairline)
            ) { Text("Kolo") }

            OutlinedButton(
                onClick = {
                    haptics.tap()
                    running = false
                    accumulated = 0
                    display = 0
                    laps.clear()
                    StopwatchService.stop(context)
                    StopwatchState.clear(context)
                    WidgetRefresh.requestUpdate(context)
                },
                enabled = display > 0 && !running,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                border = BorderStroke(1.dp, Hairline)
            ) { Text("Reset") }
        }
        Spacer(Modifier.height(14.dp))
    }
}
