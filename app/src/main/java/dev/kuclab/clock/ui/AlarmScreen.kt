package dev.kuclab.clock.ui

import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kuclab.clock.Alarm
import dev.kuclab.clock.AlarmScheduler
import dev.kuclab.clock.Alarms
import dev.kuclab.clock.BuiltInTones
import dev.kuclab.clock.WidgetRefresh
import kotlin.random.Random

private val shortDays = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")

// Millisecond timestamp + a random sub-component: two alarms created within the same
// second (fast double-tap, scripted UI) can never collide the way a pure epoch-second id
// used to, which previously let one alarm's schedule silently overwrite another's.
private fun newAlarmId(): Long = System.currentTimeMillis() * 1000 + Random.nextInt(0, 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen() {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pm = ctx.getSystemService(PowerManager::class.java)
    var alarms by remember { mutableStateOf(Alarms.load(ctx)) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showBattery by remember { mutableStateOf(false) }

    fun applySchedule(alarm: Alarm) {
        // Always clear any stale once/repeat registration for this id first, so an
        // old snoozed instance can never linger and ring after the alarm was edited.
        AlarmScheduler.cancel(ctx, alarm)
        if (alarm.enabled) {
            AlarmScheduler.schedule(ctx, alarm)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                showBattery = true
            }
        }
        WidgetRefresh.requestUpdate(ctx)
    }

    val showDialog = showNew || editing != null
    if (showDialog) {
        AlarmEditDialog(
            initial = editing,
            onDismiss = { haptics.tap(); showNew = false; editing = null },
            onSave = { a ->
                haptics.confirm()
                alarms = Alarms.upsert(ctx, a)
                applySchedule(a)
                showNew = false
                editing = null
            },
            onDelete = { a ->
                haptics.confirm()
                alarms = Alarms.remove(ctx, a.id)
                AlarmScheduler.cancel(ctx, a)
                WidgetRefresh.requestUpdate(ctx)
                showNew = false
                editing = null
            }
        )
    }

    if (showBattery) {
        AlertDialog(
            onDismissRequest = { showBattery = false },
            containerColor = SurfaceBg,
            shape = MaterialTheme.shapes.large,
            title = { Text("Spolehlivé buzení") },
            text = {
                Text("Aby budík zvonil i při úsporném režimu baterie, povolte aplikaci neomezené používání baterie.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showBattery = false
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }) { Text("Povolit", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showBattery = false }) { Text("Nyní ne") }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.hairlineCard(shape = CircleShape).padding(22.dp)
                ) {
                    Icon(AppIcons.Budik, contentDescription = null, tint = Accent)
                }
                Spacer(Modifier.height(18.dp))
                Text("Zatím žádné budíky", color = OnDark, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Klepněte na + a nastavte první budík",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms.sortedBy { it.hour * 60 + it.minute }, key = { it.id }) { a ->
                    AlarmCard(
                        alarm = a,
                        onClick = { haptics.tap(); editing = a },
                        onToggle = { on ->
                            haptics.tap()
                            alarms = Alarms.setEnabled(ctx, a.id, on)
                            applySchedule(a.copy(enabled = on))
                        }
                    )
                }
            }
        }

        val fabInteraction = remember { MutableInteractionSource() }
        val fabScale by rememberPressScale(fabInteraction)
        FloatingActionButton(
            onClick = { haptics.confirm(); showNew = true },
            interactionSource = fabInteraction,
            containerColor = Accent,
            contentColor = OnAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Nový budík")
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .hairlineCard(borderAlpha = if (alarm.enabled) 1f else 0.5f)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                String.format("%02d:%02d", alarm.hour, alarm.minute),
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (alarm.enabled) OnDark else Muted
            )
            if (alarm.label.isNotBlank()) {
                Text(
                    alarm.label,
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                Alarm.daySummary(alarm.days),
                color = if (alarm.enabled) Accent else Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent)
        )
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
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // skipPartiallyExpanded: the sheet used to open only to a half-height "peek" state that
    // had to be dragged up manually before you could see or use the bottom half of the
    // form - this forces it straight to full height every time it opens.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: 7,
        initialMinute = initial?.minute ?: 0,
        is24Hour = true
    )
    var days by remember(initial?.id) { mutableStateOf(initial?.days ?: emptyList()) }
    var label by remember(initial?.id) { mutableStateOf(initial?.label ?: "") }
    var snooze by remember(initial?.id) { mutableStateOf(initial?.snooze ?: true) }
    var snoozeMinutes by remember(initial?.id) { mutableStateOf((initial?.snoozeMinutes ?: 5).toFloat()) }
    var math by remember(initial?.id) { mutableStateOf(initial?.math ?: true) }
    var mathCount by remember(initial?.id) { mutableStateOf((initial?.mathCount ?: 3).toFloat()) }
    var ringtoneUri by remember(initial?.id) { mutableStateOf(initial?.ringtoneUri) }

    // The M3 TimePicker has no built-in haptic callback of its own, so a tick is fired here
    // any time the chosen hour or minute actually changes.
    androidx.compose.runtime.LaunchedEffect(timeState.hour, timeState.minute) {
        haptics.tap()
    }

    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose { previewPlayer?.release() }
    }
    fun playPreview(resId: Int) {
        previewPlayer?.release()
        previewPlayer = try {
            MediaPlayer.create(ctx, resId)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Exception) {
            null
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ringtoneUri = uri?.toString()
    }
    fun openSystemPicker() {
        haptics.tap()
        val existing = ringtoneUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Vyzvánění budíku")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        ringtonePicker.launch(intent)
    }

    val builtIn = BuiltInTones.byUri(ctx, ringtoneUri)
    val ringtoneSummary = when {
        builtIn != null -> builtIn.label
        ringtoneUri != null -> "Vlastní tón"
        else -> "Výchozí tón zařízení"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBg
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (initial == null) "Nový budík" else "Upravit budík",
                style = MaterialTheme.typography.titleLarge,
                color = OnDark
            )
            Spacer(Modifier.height(10.dp))
            TimePicker(
                state = timeState,
                colors = TimePickerDefaults.colors(
                    selectorColor = Accent,
                    periodSelectorSelectedContainerColor = Accent,
                    periodSelectorSelectedContentColor = OnAccent,
                    timeSelectorSelectedContainerColor = Accent,
                    timeSelectorSelectedContentColor = OnAccent
                )
            )
            Spacer(Modifier.height(16.dp))
            Text("Opakování", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shortDays.forEachIndexed { i, name ->
                    val day = i + 1
                    val selected = day in days
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.tap()
                            days = if (selected) days.filterNot { it == day } else days + day
                        },
                        label = { Text(name, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBg,
                            labelColor = Muted,
                            selectedContainerColor = Accent,
                            selectedLabelColor = OnAccent
                        )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Popisek") },
                placeholder = { Text("např. Práce") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Text("Vyzvánění", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BuiltInTones.all.forEach { tone ->
                    val selected = builtIn?.id == tone.id
                    FilterChip(
                        selected = selected,
                        onClick = {
                            haptics.tap()
                            ringtoneUri = BuiltInTones.uriFor(ctx, tone.resId).toString()
                            playPreview(tone.resId)
                        },
                        label = { Text(tone.label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBg,
                            labelColor = Muted,
                            selectedContainerColor = Accent,
                            selectedLabelColor = OnAccent
                        )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .hairlineCard()
                    .clickable(onClick = ::openSystemPicker)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Jiný tón…", color = OnDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(ringtoneSummary, color = Accent, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Odložení", Modifier.weight(1f), color = OnDark)
                Switch(
                    checked = snooze,
                    onCheckedChange = { haptics.tap(); snooze = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
            if (snooze) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Odložit o ${snoozeMinutes.toInt()} min", color = OnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    HapticSlider(
                        value = snoozeMinutes,
                        onValueChange = { snoozeMinutes = it },
                        valueRange = 1f..30f,
                        stepSize = 1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Vypnutí jen po vyřešení příkladů", Modifier.weight(1f), color = OnDark)
                Switch(
                    checked = math,
                    onCheckedChange = { haptics.tap(); math = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }
            if (math) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Počet příkladů: ${mathCount.toInt()}", color = OnDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    HapticSlider(
                        value = mathCount,
                        onValueChange = { mathCount = it },
                        valueRange = 1f..5f,
                        stepSize = 1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val saveInteraction = remember { MutableInteractionSource() }
                val saveScale by rememberPressScale(saveInteraction)
                Button(
                    onClick = {
                        onSave(
                            Alarm(
                                id = initial?.id ?: newAlarmId(),
                                hour = timeState.hour,
                                minute = timeState.minute,
                                enabled = initial?.enabled ?: true,
                                days = days.sorted(),
                                label = label.trim(),
                                snooze = snooze,
                                math = math,
                                snoozeMinutes = snoozeMinutes.toInt(),
                                mathCount = mathCount.toInt(),
                                ringtoneUri = ringtoneUri
                            )
                        )
                    },
                    interactionSource = saveInteraction,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.graphicsLayer { scaleX = saveScale; scaleY = saveScale },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    )
                ) { Text("Uložit", fontWeight = FontWeight.Bold) }
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("Smazat", color = ErrRed)
                    }
                }
            }
        }
    }
}
