package dev.kuclab.clock.ui

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.Alarm
import dev.kuclab.clock.AlarmScheduler
import dev.kuclab.clock.Alarms
import dev.kuclab.clock.BuiltInTones
import dev.kuclab.clock.WakeScene
import dev.kuclab.clock.WidgetRefresh
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val shortDays = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")

private fun newAlarmId(): Long = System.currentTimeMillis() * 1000 + Random.nextInt(0, 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val powerManager = context.getSystemService(PowerManager::class.java)
    var alarms by remember { mutableStateOf(Alarms.load(context)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showBatteryWarning by remember { mutableStateOf(false) }

    fun applySchedule(alarm: Alarm) {
        AlarmScheduler.cancel(context, alarm)
        if (alarm.enabled) {
            AlarmScheduler.schedule(context, alarm)
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                showBatteryWarning = true
            }
        }
        WidgetRefresh.requestUpdate(context)
    }

    if (showNew || editing != null) {
        AlarmEditDialog(
            initial = editing,
            onDismiss = { showNew = false; editing = null },
            onSave = { alarm ->
                haptics.confirm()
                alarms = Alarms.upsert(context, alarm)
                applySchedule(alarm)
                showNew = false
                editing = null
            },
            onDelete = { alarm ->
                haptics.confirm()
                alarms = Alarms.remove(context, alarm.id)
                AlarmScheduler.cancel(context, alarm)
                WidgetRefresh.requestUpdate(context)
                editing = null
            }
        )
    }

    if (showBatteryWarning) {
        AlertDialog(
            onDismissRequest = { showBatteryWarning = false },
            containerColor = SurfaceBg,
            title = { Text("Spolehlivé buzení") },
            text = { Text("Povolte aplikaci fungovat bez omezení baterie, aby systém budík nezpozdil.") },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryWarning = false
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("Otevřít nastavení", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showBatteryWarning = false }) { Text("Teď ne") } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Budíky", color = OnDark, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(
                onClick = { haptics.confirm(); showNew = true },
                modifier = Modifier.height(42.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Přidat", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (alarms.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Žádný budík", color = OnDark, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Přidejte čas, kdy vás má telefon vzbudit.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                Button(
                    onClick = { showNew = true },
                    modifier = Modifier.padding(top = 18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
                ) { Text("Nastavit první budík") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 18.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { AlarmOverview(alarms) }
                items(alarms.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onClick = { haptics.tap(); editing = alarm },
                        onToggle = { enabled ->
                            haptics.tap()
                            alarms = Alarms.setEnabled(context, alarm.id, enabled)
                            applySchedule(alarm.copy(enabled = enabled))
                        },
                        onSkipNext = {
                            haptics.confirm()
                            val updated = if (alarm.skippedOccurrenceAt?.let { it > System.currentTimeMillis() } == true) {
                                alarm.copy(skippedOccurrenceAt = null)
                            } else {
                                AlarmScheduler.skipNext(alarm) ?: alarm
                            }
                            AlarmScheduler.cancel(context, alarm)
                            alarms = Alarms.upsert(context, updated)
                            AlarmScheduler.schedule(context, updated)
                            WidgetRefresh.requestUpdate(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmOverview(alarms: List<Alarm>) {
    val active = alarms.count { it.enabled }
    val next = alarms.filter { it.enabled }
        .mapNotNull { alarm -> AlarmScheduler.nextTrigger(alarm)?.let { alarm to it } }
        .minByOrNull { it.second }
    Row(
        Modifier.fillMaxWidth().hairlineCard(fill = SurfaceBg).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (next == null) "Všechny budíky jsou vypnuté" else "Příští budík", color = Muted, fontSize = 12.sp)
            Text(
                next?.let { SimpleDateFormat("EEEE d. M.", Locale("cs")).format(Date(it.second)).replaceFirstChar { c -> c.uppercase(Locale("cs")) } }
                    ?: "$active aktivních",
                color = OnDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        next?.let {
            Text(
                SimpleDateFormat("HH:mm", Locale("cs")).format(Date(it.second)),
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSkipNext: () -> Unit
) {
    val skipped = alarm.skippedOccurrenceAt?.let { it > System.currentTimeMillis() } == true
    Column(
        Modifier.fillMaxWidth().hairlineCard().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    String.format("%02d:%02d", alarm.hour, alarm.minute),
                    color = if (alarm.enabled) OnDark else Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    alarm.label.ifBlank { Alarm.daySummary(alarm.days) },
                    color = if (alarm.enabled) Accent else Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent)
            )
        }
        Text(
            "Hlasitost ${alarm.volumePercent}% · ${if (alarm.fadeInSeconds == 0) "bez náběhu" else "náběh ${alarm.fadeInSeconds} s"}" +
                if (alarm.wakeCheckEnabled) " · kontrola za ${alarm.wakeCheckDelayMinutes} min" else "",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 9.dp)
        )
        if (alarm.days.isNotEmpty() && alarm.enabled) {
            TextButton(
                onClick = onSkipNext,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    if (skipped) "Příští zvonění je přeskočeno · Obnovit" else "Přeskočit pouze příští zvonění",
                    color = if (skipped) Muted else Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditDialog(
    initial: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hourText by remember(initial?.id) { mutableStateOf(String.format("%02d", initial?.hour ?: 7)) }
    var minuteText by remember(initial?.id) { mutableStateOf(String.format("%02d", initial?.minute ?: 0)) }
    var days by remember(initial?.id) { mutableStateOf(initial?.days ?: emptyList()) }
    var label by remember(initial?.id) { mutableStateOf(initial?.label ?: "") }
    var snooze by remember(initial?.id) { mutableStateOf(initial?.snooze ?: true) }
    var snoozeMinutes by remember(initial?.id) { mutableStateOf((initial?.snoozeMinutes ?: 5).toFloat()) }
    var math by remember(initial?.id) { mutableStateOf(initial?.math ?: true) }
    var mathCount by remember(initial?.id) { mutableStateOf((initial?.mathCount ?: 3).toFloat()) }
    var stepsRequired by remember(initial?.id) { mutableStateOf(initial?.stepsRequired ?: false) }
    var stepsCount by remember(initial?.id) { mutableStateOf((initial?.stepsCount ?: 20).toFloat()) }
    var shakeRequired by remember(initial?.id) { mutableStateOf(initial?.shakeRequired ?: false) }
    var shakeCount by remember(initial?.id) { mutableStateOf((initial?.shakeCount ?: 15).toFloat()) }
    var ringtoneUri by remember(initial?.id) { mutableStateOf(initial?.ringtoneUri) }
    var volumePercent by remember(initial?.id) { mutableStateOf((initial?.volumePercent ?: 80).toFloat()) }
    var fadeInSeconds by remember(initial?.id) { mutableStateOf((initial?.fadeInSeconds ?: 20).toFloat()) }
    var wakeScene by remember(initial?.id) { mutableStateOf(WakeScene.fromId(initial?.wakeScene)) }
    var wakeMessage by remember(initial?.id) { mutableStateOf(initial?.wakeMessage ?: "") }
    var wakeCheckEnabled by remember(initial?.id) { mutableStateOf(initial?.wakeCheckEnabled ?: false) }
    var wakeCheckDelay by remember(initial?.id) { mutableStateOf((initial?.wakeCheckDelayMinutes ?: 5).toFloat()) }
    var wakeCheckTimeout by remember(initial?.id) { mutableStateOf((initial?.wakeCheckTimeoutMinutes ?: 2).toFloat()) }
    var expandedSection by remember(initial?.id) { mutableStateOf<String?>(null) }

    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { previewPlayer?.release() } }
    fun playPreview(resId: Int) {
        previewPlayer?.release()
        previewPlayer = runCatching {
            MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }.getOrNull()
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ringtoneUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
    }
    fun openSystemPicker() {
        haptics.tap()
        val existing = ringtoneUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        ringtonePicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Zvuk budíku")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        )
    }

    val builtIn = BuiltInTones.byUri(context, ringtoneUri)
    val ringtoneSummary = when {
        builtIn != null -> builtIn.label
        ringtoneUri != null -> "Vlastní tón"
        else -> "Výchozí tón"
    }
    val dismissalSummary = listOfNotNull(
        if (math) "${mathCount.toInt()} příklady" else null,
        if (stepsRequired) "${stepsCount.toInt()} kroků" else null,
        if (shakeRequired) "${shakeCount.toInt()} zatřesení" else null
    ).ifEmpty { listOf("Bez úkolu") }.joinToString(" · ")

    fun toggleSection(key: String) {
        haptics.tap()
        expandedSection = if (expandedSection == key) null else key
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBg,
        dragHandle = null
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 32.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (initial == null) "Nový budík" else "Upravit budík", color = OnDark, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Zrušit", color = Muted) }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { input ->
                        val digits = input.filter(Char::isDigit).take(2)
                        if (digits.isEmpty() || (digits.toIntOrNull() ?: 0) <= 23) hourText = digits
                    },
                    label = { Text("Hodina") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 36.sp, color = OnDark),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(":", color = Muted, fontSize = 34.sp, modifier = Modifier.padding(horizontal = 10.dp))
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { input ->
                        val digits = input.filter(Char::isDigit).take(2)
                        if (digits.isEmpty() || (digits.toIntOrNull() ?: 0) <= 59) minuteText = digits
                    },
                    label = { Text("Minuta") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 36.sp, color = OnDark),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Opakování", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                shortDays.forEachIndexed { index, name ->
                    val day = index + 1
                    val selected = day in days
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Accent else CardBg)
                            .clickable {
                                haptics.tap()
                                days = if (selected) days - day else days + day
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = if (selected) OnAccent else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text("Název") },
                placeholder = { Text("Práce, škola…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )

            Spacer(Modifier.height(18.dp))
            EditorSection(
                title = "Zvuk a hlasitost",
                summary = "$ringtoneSummary · ${volumePercent.toInt()}%" + if (fadeInSeconds > 0) " · náběh ${fadeInSeconds.toInt()} s" else "",
                expanded = expandedSection == "sound",
                onClick = { toggleSection("sound") }
            ) {
                Text("Zvuk", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuiltInTones.all.forEach { tone ->
                        FilterChip(
                            selected = builtIn?.id == tone.id,
                            onClick = {
                                haptics.tap()
                                ringtoneUri = BuiltInTones.uriFor(context, tone.resId).toString()
                                playPreview(tone.resId)
                            },
                            label = { Text(tone.label) },
                            colors = compactChipColors()
                        )
                    }
                    FilterChip(
                        selected = builtIn == null,
                        onClick = ::openSystemPicker,
                        label = { Text("Jiný…") },
                        colors = compactChipColors()
                    )
                }
                ValueSlider("Hlasitost", "${volumePercent.toInt()} %", volumePercent, { volumePercent = it }, 10f..100f, 5f)
                ValueSlider(
                    "Postupné zesílení",
                    if (fadeInSeconds == 0f) "Vypnuto" else "${fadeInSeconds.toInt()} s",
                    fadeInSeconds,
                    { fadeInSeconds = it },
                    0f..60f,
                    5f
                )
                Text("Před zvoněním se hlasitost nastaví a po vypnutí se vrátí.", color = Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(10.dp))
            EditorSection(
                title = "Odložení",
                summary = if (snooze) "O ${snoozeMinutes.toInt()} minut" else "Vypnuto",
                expanded = expandedSection == "snooze",
                onClick = { toggleSection("snooze") },
                trailing = {
                    Switch(
                        checked = snooze,
                        onCheckedChange = { haptics.tap(); snooze = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent)
                    )
                }
            ) {
                if (snooze) ValueSlider("Délka odložení", "${snoozeMinutes.toInt()} min", snoozeMinutes, { snoozeMinutes = it }, 1f..30f, 1f)
                else Text("Tlačítko Odložit se při zvonění nezobrazí.", color = Muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(10.dp))
            EditorSection(
                title = "Způsob vypnutí",
                summary = dismissalSummary,
                expanded = expandedSection == "dismiss",
                onClick = { toggleSection("dismiss") }
            ) {
                ToggleSetting("Matematické příklady", "Probouzí soustředění", math) { math = it }
                if (math) ValueSlider("Počet příkladů", mathCount.toInt().toString(), mathCount, { mathCount = it }, 1f..5f, 1f)
                ToggleSetting("Kroky", "Telefon musí zaznamenat chůzi", stepsRequired) { stepsRequired = it }
                if (stepsRequired) ValueSlider("Počet kroků", stepsCount.toInt().toString(), stepsCount, { stepsCount = it }, 5f..100f, 5f)
                ToggleSetting("Zatřesení", "Telefonem je potřeba zatřást", shakeRequired) { shakeRequired = it }
                if (shakeRequired) ValueSlider("Počet zatřesení", shakeCount.toInt().toString(), shakeCount, { shakeCount = it }, 5f..40f, 5f)
            }

            Spacer(Modifier.height(10.dp))
            EditorSection(
                title = "Po probuzení",
                summary = wakeScene.label + if (wakeCheckEnabled) " · kontrola za ${wakeCheckDelay.toInt()} min" else "",
                expanded = expandedSection == "morning",
                onClick = { toggleSection("morning") }
            ) {
                Text("Vzhled obrazovky zvonění", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WakeScene.entries.forEach { scene ->
                        FilterChip(
                            selected = wakeScene == scene,
                            onClick = { haptics.tap(); wakeScene = scene },
                            label = { Text(scene.label) },
                            colors = compactChipColors()
                        )
                    }
                }
                OutlinedTextField(
                    value = wakeMessage,
                    onValueChange = { wakeMessage = it.take(80) },
                    label = { Text("Zpráva na ráno") },
                    placeholder = { Text("Dnes začni pomalu.") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                ToggleSetting(
                    "Ověřit, že jsem vzhůru",
                    "Bez potvrzení se budík spustí znovu",
                    wakeCheckEnabled
                ) { wakeCheckEnabled = it }
                if (wakeCheckEnabled) {
                    ValueSlider("Zeptat se za", "${wakeCheckDelay.toInt()} min", wakeCheckDelay, { wakeCheckDelay = it }, 1f..15f, 1f)
                    ValueSlider("Čas na odpověď", "${wakeCheckTimeout.toInt()} min", wakeCheckTimeout, { wakeCheckTimeout = it }, 1f..5f, 1f)
                }
            }

            Button(
                onClick = {
                    onSave(
                        Alarm(
                            id = initial?.id ?: newAlarmId(),
                            hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 0,
                            minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                            enabled = initial?.enabled ?: true,
                            days = days.sorted(),
                            label = label.trim(),
                            snooze = snooze,
                            math = math,
                            snoozeMinutes = snoozeMinutes.toInt(),
                            mathCount = mathCount.toInt(),
                            ringtoneUri = ringtoneUri,
                            stepsRequired = stepsRequired,
                            stepsCount = stepsCount.toInt(),
                            shakeRequired = shakeRequired,
                            shakeCount = shakeCount.toInt(),
                            volumePercent = volumePercent.toInt(),
                            fadeInSeconds = fadeInSeconds.toInt(),
                            wakeScene = wakeScene.id,
                            wakeMessage = wakeMessage.trim(),
                            wakeCheckEnabled = wakeCheckEnabled,
                            wakeCheckDelayMinutes = wakeCheckDelay.toInt(),
                            wakeCheckTimeoutMinutes = wakeCheckTimeout.toInt(),
                            skippedOccurrenceAt = initial?.skippedOccurrenceAt?.takeIf {
                                initial.hour == (hourText.toIntOrNull() ?: 0) &&
                                    initial.minute == (minuteText.toIntOrNull() ?: 0) &&
                                    initial.days == days.sorted()
                            }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) { Text("Uložit budík", fontWeight = FontWeight.SemiBold) }

            if (initial != null) {
                TextButton(onClick = { onDelete(initial) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Smazat budík", color = ErrRed)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun compactChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = CardBg,
    labelColor = Muted,
    selectedContainerColor = Accent,
    selectedLabelColor = OnAccent
)

@Composable
private fun EditorSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().hairlineCard()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OnDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(summary, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (trailing != null) trailing()
            else Text(if (expanded) "⌃" else "⌄", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Hairline))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ToggleSetting(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = OnDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = { haptics.tap(); onCheckedChange(it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = OnAccent)
        )
    }
}

@Composable
private fun ValueSlider(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = OnDark, fontSize = 13.sp)
            Text(valueText, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        HapticSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
            stepSize = step
        )
    }
}
