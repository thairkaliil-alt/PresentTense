package com.allinone.blocker.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockedWebsite
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.data.UrlExtractor
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedWebsitesScreen(onBack: () -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked websites", color = TextPrimary) },
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
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add website") }
            )
        },
        containerColor = BgScreen
    ) { pad ->
        BlockedWebsitesList(modifier = Modifier.padding(pad))
    }

    if (showAddDialog) {
        AddWebsiteDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { domain ->
                BlockerRepository.upsertWebsite(BlockedWebsite(domain = domain, enabled = true))
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BlockedWebsitesList(modifier: Modifier = Modifier) {
    val websites by BlockerRepository.websites.collectAsState()

    if (websites.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "No websites blocked yet.\nTap \"Add website\" to block one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Works inside Chrome, Firefox, and most other browsers.\nPrivate/incognito tabs can't be detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    } else {
        LazyColumn(
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(websites, key = { it.domain }) { site ->
                BlockedWebsiteRow(site = site)
            }
            item { Spacer(Modifier.size(72.dp)) }
        }
    }
}

@Composable
private fun BlockedWebsiteRow(site: BlockedWebsite) {
    val onToggle: (Boolean) -> Unit = remember(site) {
        { wantsOn ->
            if (wantsOn) {
                BlockerRepository.upsertWebsite(site.copy(enabled = true))
            } else {
                StrictModeGate.guard {
                    BlockerRepository.upsertWebsite(site.copy(enabled = false))
                }
            }
        }
    }
    val onDelete: () -> Unit = remember(site.domain) {
        { StrictModeGate.guard { BlockerRepository.removeWebsite(site.domain) } }
    }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    site.domain,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    if (site.enabled) "Blocked" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = site.enabled,
                onCheckedChange = onToggle,
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

@Composable
private fun AddWebsiteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val normalized = remember(input) { UrlExtractor.normalizeDomain(input) }
    val isValid = normalized != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block a website") },
        text = {
            Column {
                Text(
                    "Enter the website's domain, like \"reddit.com\" or \"youtube.com\". This blocks it inside any browser, not just one app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Domain") },
                    placeholder = { Text("reddit.com") },
                    singleLine = true,
                    isError = input.isNotBlank() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (input.isNotBlank() && !isValid) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "That doesn't look like a valid domain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let(onConfirm) },
                enabled = isValid
            ) { Text("Block") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
