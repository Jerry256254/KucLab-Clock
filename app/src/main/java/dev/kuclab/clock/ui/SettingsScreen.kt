package dev.kuclab.clock.ui

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.kuclab.clock.BuildConfig
import dev.kuclab.clock.hasNotificationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GITHUB_OWNER_REPO = "Jerry256254/KucLab-Clock"
private const val GITHUB_URL = "https://github.com/$GITHUB_OWNER_REPO"

private fun fetchLatestRelease(): Pair<String?, String?> {
    var connection: java.net.HttpURLConnection? = null
    return try {
        connection = (java.net.URL("https://api.github.com/repos/$GITHUB_OWNER_REPO/releases/latest")
            .openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        json.optString("tag_name", "").removePrefix("v").ifBlank { null } to
            json.optString("html_url", "$GITHUB_URL/releases/latest")
    } catch (_: Exception) {
        null to null
    } finally {
        connection?.disconnect()
    }
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.split('.').map { it.toIntOrNull() ?: 0 }
    val localParts = local.split('.').map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
        val r = remoteParts.getOrElse(index) { 0 }
        val l = localParts.getOrElse(index) { 0 }
        if (r != l) return r > l
    }
    return false
}

private fun Context.findActivity(): Activity? {
    var value: Context = this
    while (value is ContextWrapper) {
        if (value is Activity) return value
        value = value.baseContext
    }
    return null
}

private data class PermissionItem(
    val name: String,
    val description: String,
    val granted: Boolean,
    val action: () -> Unit
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val activity = remember { context.findActivity() }
    val powerManager = context.getSystemService(PowerManager::class.java)
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeSignal by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeSignal++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    val permissions = remember(resumeSignal) {
        listOf(
            PermissionItem(
                "Oznámení",
                "Zvonění a ovládání na pozadí",
                hasNotificationPermission(context)
            ) { activity?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11) },
            PermissionItem(
                "Přesné budíky",
                "Spuštění přesně v nastavený čas",
                Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
            ) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            },
            PermissionItem(
                "Baterie bez omezení",
                "Spolehlivost při úsporném režimu",
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            ) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }
            },
            PermissionItem(
                "Zamčená obrazovka",
                "Plnoobrazovkové buzení",
                Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()
            ) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        Text("Nastavení", color = OnDark, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Aby budík fungoval i se zhasnutým telefonem.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
        )

        SectionLabel("Spolehlivost")
        Column(Modifier.fillMaxWidth().hairlineCard()) {
            permissions.forEachIndexed { index, item ->
                PermissionRow(item) {
                    haptics.confirm()
                    item.action()
                }
                if (index < permissions.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Hairline))
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("Aplikace")
        Column(Modifier.fillMaxWidth().hairlineCard().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("KucLab Clock", color = OnDark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Verze ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(8.dp).background(Accent, CircleShape))
            }
            Text(
                "Budíky, časovač a stopky bez reklam a sledování.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    haptics.tap()
                    checkingUpdate = true
                    updateStatus = null
                    updateUrl = null
                    scope.launch {
                        val (latest, url) = withContext(Dispatchers.IO) { fetchLatestRelease() }
                        checkingUpdate = false
                        when {
                            latest == null -> updateStatus = "Kontrola se nezdařila. Zkuste to znovu."
                            isNewerVersion(latest, BuildConfig.VERSION_NAME) -> {
                                updateStatus = "Je dostupná verze $latest."
                                updateUrl = url
                            }
                            else -> updateStatus = "Používáte nejnovější verzi."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                shape = RoundedCornerShape(10.dp)
            ) { Text(if (checkingUpdate) "Kontroluji…" else "Zkontrolovat aktualizace", fontWeight = FontWeight.SemiBold) }

            updateStatus?.let { message ->
                Text(message, color = if (updateUrl != null) Accent else Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                if (updateUrl != null) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))) }
                    }) { Text("Stáhnout verzi", color = Accent) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clickable {
                haptics.tap()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))) }
            }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Zdrojový kód", color = OnDark, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("GitHub  ›", color = Accent, fontSize = 13.sp)
        }
        Text("Licence MIT", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
}

@Composable
private fun PermissionRow(item: PermissionItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !item.granted, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.padding(end = 12.dp).size(9.dp).background(if (item.granted) Accent else Muted, CircleShape)
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, color = OnDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(item.description, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(if (item.granted) "Hotovo" else "Povolit", color = if (item.granted) Muted else Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
