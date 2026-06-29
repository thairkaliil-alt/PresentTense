package com.allinone.blocker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockerRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { InstalledApps.ensureLoaded(context) }
    val all by InstalledApps.apps.collectAsState()
    val loadingApps by InstalledApps.loading.collectAsState()

    val whitelist by BlockerRepository.whitelist.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = all.filter {
        it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whitelist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { InstalledApps.refresh(context) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh app list")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            Text(
                "Apps you turn on here stay open during any lockdown. Phone calls and texts are always allowed automatically, even if not listed below.",
                Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            if (all.isEmpty() && loadingApps) {
                Text(
                    "Loading installed apps…",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.size(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { device ->
                    val on = whitelist.contains(device.packageName)
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconOrLetter(device.packageName, device.label)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.label, fontWeight = FontWeight.Bold)
                            }
                            Switch(
                                checked = on,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        BlockerRepository.addToWhitelist(device.packageName)
                                    } else {
                                        BlockerRepository.removeFromWhitelist(device.packageName)
                                    }
                                }
                            )
                        }
                        Divider()
                    }
                }
            }
        }
    }
}
