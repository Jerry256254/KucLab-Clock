package dev.kuclab.clock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.ui.AppIcons
import dev.kuclab.clock.ui.KucLabTheme
import dev.kuclab.clock.ui.ClockScreen
import dev.kuclab.clock.ui.AlarmScreen
import dev.kuclab.clock.ui.Accent
import dev.kuclab.clock.ui.Hairline
import dev.kuclab.clock.ui.Ink
import dev.kuclab.clock.ui.Muted
import dev.kuclab.clock.ui.OnDark
import dev.kuclab.clock.ui.StopwatchScreen
import dev.kuclab.clock.ui.TimerScreen
import dev.kuclab.clock.ui.SettingsScreen
import dev.kuclab.clock.ui.rememberPressScale
import dev.kuclab.clock.ui.tap

class MainActivity : ComponentActivity() {

    // Backs both a fresh launch (set once in onCreate) and the app already running in the
    // background (updated in onNewIntent) - e.g. tapping the widget or the running-timer/
    // stopwatch notification, which both carry a "tab" extra for the relevant screen. Plain
    // mutableStateOf (not remember{}) so onNewIntent can update it from outside composition.
    private var requestedTab by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab = intent.getIntExtra("tab", -1).takeIf { it in 0..4 }

        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        // Needed for the alarm's optional step-counting "prove you're up" gate - requested
        // up front so it's already granted by the time an alarm actually rings.
        if (Build.VERSION.SDK_INT >= 29 &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 10)
        }

        setContent {
            KucLabTheme {
                MainScreen(requestedTab) { requestedTab = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getIntExtra("tab", -1).takeIf { it in 0..4 }?.let { requestedTab = it }
    }
}

private data class TabDef(val name: String, val icon: ImageVector)

private val tabs = listOf(
    TabDef("Hodiny", AppIcons.Hodiny),
    TabDef("Budík", AppIcons.Budik),
    TabDef("Stopky", AppIcons.Stopky),
    TabDef("Časovač", AppIcons.Casovac),
    TabDef("Nastavení", AppIcons.Nastaveni)
)

@Composable
fun MainScreen(requestedTab: Int? = null, onRequestedTabConsumed: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            onRequestedTabConsumed()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Box(Modifier.fillMaxSize().padding(bottom = 76.dp)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabContent"
            ) { t ->
                stateHolder.SaveableStateProvider(t) {
                    when (t) {
                        0 -> ClockScreen()
                        1 -> AlarmScreen()
                        2 -> StopwatchScreen()
                        3 -> TimerScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }

        BottomBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun BottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth().background(Ink)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 10.dp)
        ) {
            tabs.forEachIndexed { i, t ->
                val isSelected = selected == i
                val interaction = remember { MutableInteractionSource() }
                val scale by rememberPressScale(interaction)
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) {
                            if (!isSelected) {
                                haptics.tap()
                                onSelect(i)
                            }
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        t.icon,
                        contentDescription = t.name,
                        tint = if (isSelected) Accent else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        t.name,
                        fontSize = 10.sp,
                        color = if (isSelected) OnDark else Muted,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .size(if (isSelected) 4.dp else 0.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    )
                }
            }
        }
    }
}
