package dev.kuclab.clock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.AlarmScheduler
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    var hourMinute by remember { mutableStateOf("00:00") }
    var seconds by remember { mutableStateOf("00") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("EEEE, d. MMMM", Locale("cs"))
        while (true) {
            val now = Calendar.getInstance()
            hourMinute = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            seconds = String.format("%02d", now.get(Calendar.SECOND))
            date = formatter.format(now.time).replaceFirstChar { it.uppercase(Locale("cs")) }
            delay(200)
        }
    }

    val nextAlarm = remember(hourMinute) { AlarmScheduler.nextUpcoming(context) }
    val greeting = remember(hourMinute) {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "Dobré ráno"
            in 11..17 -> "Dobré odpoledne"
            else -> "Dobrý večer"
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hodiny", color = OnDark, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(date, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(greeting, color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    hourMinute,
                    color = OnDark,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 82.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-3).sp
                )
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = seconds,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "seconds"
                ) { value ->
                    Text(
                        value,
                        color = Accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }

        nextAlarm?.let { (alarm, triggerAt) ->
            Column(
                Modifier.fillMaxWidth().hairlineCard().padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("Příští budík", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        SimpleDateFormat("EEEE", Locale("cs")).format(Date(triggerAt))
                            .replaceFirstChar { it.uppercase(Locale("cs")) },
                        color = OnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        SimpleDateFormat("HH:mm", Locale("cs")).format(Date(triggerAt)),
                        color = Accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    listOfNotNull(
                        alarm.label.takeIf { it.isNotBlank() },
                        "hlasitost ${alarm.volumePercent}%",
                        if (alarm.wakeCheckEnabled) "kontrola probuzení" else null
                    ).joinToString(" · "),
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } ?: Column(Modifier.fillMaxWidth().hairlineCard().padding(18.dp)) {
            Text("Žádný budík není zapnutý", color = OnDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Zapněte budík v záložce Budíky.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
}
