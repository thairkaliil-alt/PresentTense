package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownSchedule
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import java.util.Calendar

// ════════════════════════════════════════════════════════════════════════════
// The dedicated schedules manager — reached via the schedule icon in the
// Lockdown screen's top bar. Everything about recurring "lock every night
// 11pm–7am"-style rules lives here: the list of schedules, the empty-state
// hint, and the add/edit dialog, all relocated from the old decluttered
// Lockdown screen without any behavior changes.
// ════════════════════════════════════════════════════════════════════════════

private val DAY_LABELS = mapOf(
    Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)
private val DAY_ORDER = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockdownSchedulesScreen(onBack: () -> Unit) {
    val schedules by BlockerRepository.schedules.collectAsState()
    var showAddSchedule by remember { mutableStateOf<LockdownSchedule?>(null) }

    Scaffold(
        containerColor = BgDarkest,
        topBar = {
            TopAppBar(
                title = { Text("Schedules", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSchedule = LockdownSchedule(id = BlockerRepository.newScheduleId()) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add schedule")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier            = Modifier.padding(pad).fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (schedules.isEmpty()) {
                item(key = "schedules_empty") {
                    EmptyHintCard("No schedules yet. Add one for things like \u201CLock every night 11pm\u20137am.\u201D")
                }
            } else {
                items(schedules, key = { "schedule_${it.id}" }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onToggle = { checked ->
                            if (!checked) StrictModeGate.guard { BlockerRepository.updateSchedule(schedule.copy(enabled = checked)) }
                            else BlockerRepository.updateSchedule(schedule.copy(enabled = checked))
                        },
                        onDelete = { StrictModeGate.guard { BlockerRepository.removeSchedule(schedule.id) } },
                        onEdit   = { showAddSchedule = schedule }
                    )
                }
            }

            item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }

    showAddSchedule?.let { editing ->
        ScheduleEditDialog(
            schedule  = editing,
            onDismiss = { showAddSchedule = null },
            onSave    = { saved ->
                if (schedules.any { it.id == saved.id }) BlockerRepository.updateSchedule(saved)
                else BlockerRepository.addSchedule(saved)
                showAddSchedule = null
            }
        )
    }
}

@Composable
private fun EmptyHintCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        border   = BorderStroke(1.dp, TextMuted.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier         = Modifier.size(44.dp).clip(CircleShape).background(TextMuted.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextTertiary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ScheduleCard(schedule: LockdownSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    Card(
        onClick  = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (schedule.label.isNotBlank()) Text(schedule.label, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${BlockEngine.formatMinutes(schedule.startMinutes)} – ${BlockEngine.formatMinutes(schedule.endMinutes)}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(
                    schedule.daysOfWeek.sortedBy { DAY_ORDER.indexOf(it) }.mapNotNull { DAY_LABELS[it] }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = TextMuted
                )
            }
            Switch(
                checked         = schedule.enabled,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentTeal)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = AccentRed.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditDialog(schedule: LockdownSchedule, onDismiss: () -> Unit, onSave: (LockdownSchedule) -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(schedule.label) }
    var start by remember { mutableStateOf(schedule.startMinutes) }
    var end   by remember { mutableStateOf(schedule.endMinutes) }
    var days  by remember { mutableStateOf(schedule.daysOfWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Lockdown schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Name (optional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pickTime(context, start) { start = it } }) { Text("From ${BlockEngine.formatMinutes(start)}") }
                    OutlinedButton(onClick = { pickTime(context, end)   { end   = it } }) { Text("To ${BlockEngine.formatMinutes(end)}") }
                }
                Text("Active on:", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_ORDER.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick  = { days = if (day in days) days - day else days + day },
                            label    = { Text(DAY_LABELS[day] ?: "") }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(schedule.copy(label = label, startMinutes = start, endMinutes = end, daysOfWeek = days)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun pickTime(context: android.content.Context, currentMinutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, currentMinutes / 60, currentMinutes % 60, false).show()
}
