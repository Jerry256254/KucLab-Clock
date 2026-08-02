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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ClockScreen() {
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

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }
    }
}
