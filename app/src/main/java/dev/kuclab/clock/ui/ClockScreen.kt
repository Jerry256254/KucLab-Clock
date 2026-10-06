package dev.kuclab.clock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import dev.kuclab.clock.AlarmScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ClockScreen() {
    val ctx = LocalContext.current
    var hourMin by remember { mutableStateOf("00:00") }
    var seconds by remember { mutableStateOf("00") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("EEEE, d. MMMM yyyy", Locale("cs"))
        while (true) {
            val c = Calendar.getInstance()
            hourMin = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            seconds = String.format("%02d", c.get(Calendar.SECOND))
            date = fmt.format(c.time)
            delay(200)
        }
    }

    val nextAlarm = remember(hourMin) { AlarmScheduler.nextUpcoming(ctx) }
    val greeting = remember(hourMin) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..10 -> "Dobré ráno"
            in 11..17 -> "Dobré odpoledne"
            else -> "Dobrý večer"
        }
    }

    Box(Modifier.fillMaxSize().padding(26.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                greeting,
                color = Accent,
                letterSpacing = 3.sp,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                date.replaceFirstChar { it.uppercase(Locale("cs")) },
                color = Muted,
                letterSpacing = 1.5.sp,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(22.dp))
            Text(
                hourMin,
                color = OnDark,
                fontSize = 92.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-2).sp
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.hairlinePill().padding(horizontal = 14.dp, vertical = 6.dp)) {
                AnimatedContent(
                    targetState = seconds,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "seconds"
                ) { s ->
                    Text(
                        "$s s",
                        color = Accent,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            nextAlarm?.let { (alarm, at) ->
                Spacer(Modifier.height(28.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Text("DALŠÍ PROBUZENÍ", color = Muted, letterSpacing = 2.sp, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        SimpleDateFormat("EEEE · HH:mm", Locale("cs")).format(Date(at)),
                        color = OnDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        listOfNotNull(
                            alarm.label.takeIf { it.isNotBlank() },
                            "${alarm.volumePercent}% hlasitost",
                            if (alarm.wakeCheckEnabled) "kontrola probuzení" else null
                        ).joinToString("  ·  "),
                        color = Accent,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
