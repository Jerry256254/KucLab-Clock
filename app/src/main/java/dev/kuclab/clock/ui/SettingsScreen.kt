package dev.kuclab.clock.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.BuildConfig
import dev.kuclab.clock.hasNotificationPermission
import kotlin.math.roundToInt

private const val GITHUB_URL = "https://github.com/Jerry256254/KucLab-Clock"

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private data class PermItem(
    val name: String,
    val desc: String,
    val granted: Boolean,
    val buttonText: String,
    val action: () -> Unit
)

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val activity = remember { ctx.findActivity() }
    val pm = ctx.getSystemService(PowerManager::class.java)
    val am = ctx.getSystemService(AlarmManager::class.java)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val audio = ctx.getSystemService(AudioManager::class.java)
    val maxAlarmVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).toFloat() }
    var alarmVolume by remember {
        mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_ALARM).toFloat())
    }

    val items = remember {
        listOf(
            PermItem(
                "Oznámení",
                "Nutné pro zvonění budíku na pozadí a plnoobrazovkové buzení",
                granted = hasNotificationPermission(ctx),
                buttonText = "Povolit"
            ) {
                activity?.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11
                )
            },
            PermItem(
                "Přesné časování budíků",
                "Zaručí, že budík zazvoní přesně v nastavenou dobu",
                granted = am.canScheduleExactAlarms(),
                buttonText = "Zapnout"
            ) {
                try {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                } catch (_: Exception) {
                }
            },
            PermItem(
                "Bez omezení baterie",
                "Budík zazvoní i v úsporném režimu baterie",
                granted = pm.isIgnoringBatteryOptimizations(ctx.packageName),
                buttonText = "Povolit"
            ) {
                try {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                } catch (_: Exception) {
                }
            },
            PermItem(
                "Buzení na zamčené obrazovce",
                "Budík se zobrazí a bude zvonit i při zamčeném displeji",
                granted = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent(),
                buttonText = "Zapnout"
            ) {
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                } catch (_: Exception) {
                }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "HLASITOST",
            color = Accent,
            letterSpacing = 3.sp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .hairlineCard()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${if (maxAlarmVolume > 0) (alarmVolume / maxAlarmVolume * 100).roundToInt() else 0}%",
                color = OnDark,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(4.dp))
            Text("Hlasitost budíku", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            HapticSlider(
                value = alarmVolume,
                onValueChange = { v ->
                    alarmVolume = v
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_ALARM, v.roundToInt(), 0)
                    } catch (_: SecurityException) {
                    }
                },
                valueRange = 0f..maxAlarmVolume.coerceAtLeast(1f),
                stepSize = 1f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "OPRÁVNĚNÍ",
            color = Accent,
            letterSpacing = 3.sp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Text(
            "Pro spolehlivé a garantované buzení je potřeba udělit všechna oprávnění níže. " +
                "Systém otevře příslušné obrazovky, kde je povolíte.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )

        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .hairlineCard(borderAlpha = if (item.granted) 0.6f else 1f)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        color = OnDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        item.desc,
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (item.granted) {
                    Text(
                        "POVOLENO",
                        color = OnDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else {
                    TextButton(onClick = { haptics.confirm(); item.action() }) {
                        Text(item.buttonText, color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .hairlineCard()
                .padding(16.dp)
        ) {
            Text(
                "Jak budík probouzí",
                color = OnDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "1. Budík zazvoní přesně v nastavený čas a zobrazí se přes celou obrazovku, " +
                    "i když je telefon zamčený.\n" +
                    "2. Zvonění sílí a telefon vibruje, dokud budík nevypnete.\n" +
                    "3. Budík nelze vypnout tlačítkem zpět, přepnutím na plochu ani přes " +
                    "naposledy použité aplikace — pokusí se vrátit se zpět na obrazovku, " +
                    "dokud úkol nesplníte.\n" +
                    "4. U jednotlivého budíku si můžete zapnout jeden nebo víc „vypínacích " +
                    "úkolů“: matematické příklady, pár kroků (počítá krokoměr — donutí vás " +
                    "doopravdy vstát a chodit) nebo zatřesení telefonem. Dokud nesplníte " +
                    "všechny zapnuté úkoly, budík nejde vypnout.\n" +
                    "5. Tlačítkem „Odložit“ získáte pár minut navíc, pokud jste ho u " +
                    "konkrétního budíku nevypnuli.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "Android z principu nedovolí žádné běžné appce udělat telefon úplně " +
                    "nepoužitelný (kvůli tísňovým voláním a bezpečnosti) — appka proto dělá " +
                    "vše, co je v jejích mezích možné, aby útěk co nejvíc znesnadnila.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Widget „Hodiny“ s přesnými sekundami a přehledem nejbližší události (budík / " +
                "časovač / stopky) najdete na ploše: dlouhý stisk plochy → Widgety → KucLab Clock.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(Modifier.height(26.dp))
        Text(
            "O APLIKACI",
            color = Accent,
            letterSpacing = 3.sp,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .hairlineCard()
                .padding(18.dp)
        ) {
            Text("KucLab Clock", color = OnDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Verze ${BuildConfig.VERSION_NAME}",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "Otevřený, nezávislý budík, časovač, stopky a hodiny bez reklam a sledování.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .hairlineCard(fill = Ink)
                    .clickable {
                        haptics.tap()
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                        } catch (_: Exception) {
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zdrojový kód na GitHubu", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Licence MIT — svobodný software.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
