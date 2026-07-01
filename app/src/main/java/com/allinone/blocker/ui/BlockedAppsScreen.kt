package com.allinone.blocker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockPreset
import com.allinone.blocker.data.BlockedApp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentBlueSoft
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.AccentTealSoft
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val apps by BlockerRepository.apps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked apps", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgScreen,
                    scrolledContainerColor = BgScreen
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add app") }
            )
        },
        containerColor = BgScreen
    ) { pad ->
        BlockedAppsList(
            apps = apps,
            onEdit = onEdit,
            modifier = Modifier.padding(pad)
        )
    }
}

@Composable
private fun BlockedAppsList(apps: List<BlockedApp>, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) {
        Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No apps blocked yet.\nTap \"Add app\" to choose one.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                BlockedAppRow(app = app, onEdit = onEdit)
            }
            item { Spacer(Modifier.size(72.dp)) }
        }
    }
}

@Composable
private fun BlockedAppRow(app: BlockedApp, onEdit: (String) -> Unit) {
    val icon = remember(app.packageName) { InstalledApps.iconFor(app.packageName) }
    val haptics = rememberHaptics()

    val onToggle: (Boolean) -> Unit = remember(app) {
        { wantsOn ->
            if (wantsOn) {
                BlockerRepository.upsertApp(app.copy(enabled = true))
            } else {
                StrictModeGate.guard {
                    BlockerRepository.upsertApp(app.copy(enabled = false))
                }
            }
        }
    }
    val onDelete: () -> Unit = remember(app.packageName) {
        { StrictModeGate.guard { BlockerRepository.removeApp(app.packageName) } }
    }
    val onRowClick: () -> Unit = remember(app.packageName) {
        { onEdit(app.packageName) }
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onRowClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconOrLetter(icon = icon, label = app.appName)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.appName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                val summary = remember(app.rules, app.protection, app.preset) {
                    when (app.preset) {
                        BlockPreset.MINDFUL       -> "Mindful use · 30 min/day"
                        BlockPreset.HARD_LIMITS   -> "Hard limits · time window"
                        BlockPreset.FULLY_BLOCKED -> "Fully blocked"
                        BlockPreset.CUSTOM        ->
                            if (app.rules.isEmpty()) "Custom · always blocked"
                            else "Custom · ${app.rules.size} rule${if (app.rules.size == 1) "" else "s"}"
                    }
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(
                checked = app.enabled,
                onCheckedChange = { checked -> haptics.toggleTick(); onToggle(checked) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor    = Color.White,
                    checkedTrackColor    = AccentBlue,
                    checkedBorderColor   = AccentBlue,
                    uncheckedThumbColor  = TextTertiary,
                    uncheckedTrackColor  = BgDarkest,
                    uncheckedBorderColor = TextTertiary
                )
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = TextSecondary)
            }
        }
    }
}

private val AVATAR_COLORS = listOf(
    AccentBlue,
    AccentTeal,
    AccentRed,
    AccentAmber,
    AccentBlueSoft,
    AccentTealSoft
)

@Composable
fun LetterAvatar(name: String) {
    val color = remember(name) { AVATAR_COLORS[(name.sumOf { it.code }) % AVATAR_COLORS.size] }
    val letter = if (name.isBlank()) "?" else name.trim().first().uppercase()
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppIconOrLetter(icon: ImageBitmap?, label: String) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        LetterAvatar(label)
    }
}

@Composable
fun AppIconOrLetter(packageName: String, label: String) {
    val icon = remember(packageName) { InstalledApps.iconFor(packageName) }
    AppIconOrLetter(icon = icon, label = label)
}
