package dev.kuclab.clock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.ui.Accent
import dev.kuclab.clock.ui.AlarmScreen
import dev.kuclab.clock.ui.AppBackdrop
import dev.kuclab.clock.ui.AppIcons
import dev.kuclab.clock.ui.ClockScreen
import dev.kuclab.clock.ui.Hairline
import dev.kuclab.clock.ui.KucLabTheme
import dev.kuclab.clock.ui.Muted
import dev.kuclab.clock.ui.OnDark
import dev.kuclab.clock.ui.SettingsScreen
import dev.kuclab.clock.ui.StopwatchScreen
import dev.kuclab.clock.ui.SurfaceBg
import dev.kuclab.clock.ui.TimerScreen
import dev.kuclab.clock.ui.tap

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab = intent.getIntExtra("tab", -1).takeIf { it in 0..4 }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        setContent {
            KucLabTheme { MainScreen(requestedTab) { requestedTab = null } }
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
    TabDef("Budíky", AppIcons.Budik),
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

    AppBackdrop {
        Box(Modifier.fillMaxSize().padding(bottom = 68.dp)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabContent"
            ) { current ->
                stateHolder.SaveableStateProvider(current) {
                    when (current) {
                        0 -> ClockScreen()
                        1 -> AlarmScreen()
                        2 -> StopwatchScreen()
                        3 -> TimerScreen()
                        else -> SettingsScreen()
                    }
                }
            }
        }
        BottomBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .fillMaxWidth()
            .background(SurfaceBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(Modifier.fillMaxWidth().height(68.dp)) {
            tabs.forEachIndexed { index, tab ->
                val active = selected == index
                val interaction = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = interaction, indication = null) {
                            if (!active) {
                                haptics.tap()
                                onSelect(index)
                            }
                        }
                        .padding(top = 9.dp, bottom = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        if (active) {
                            Box(Modifier.size(width = 22.dp, height = 2.dp).background(Accent))
                        }
                        Icon(
                            tab.icon,
                            contentDescription = tab.name,
                            tint = if (active) Accent else Muted,
                            modifier = Modifier.padding(top = 6.dp).size(20.dp)
                        )
                    }
                    Text(
                        tab.name,
                        color = if (active) OnDark else Muted,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
