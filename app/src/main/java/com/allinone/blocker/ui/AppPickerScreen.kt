package com.allinone.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockPreset
import com.allinone.blocker.data.BlockedApp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

// ─────────────────────────────────────────────────────────────────────────────
// "Popular to block" — these appear at the top of the list if installed.
// Order here controls the order they appear in the section.
// ─────────────────────────────────────────────────────────────────────────────
private val POPULAR_PACKAGES = listOf(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.zhiliaoapp.musically",     // TikTok
    "com.facebook.katana",          // Facebook
    "com.snapchat.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.linkedin.android",
    "com.pinterest",
    "com.netflix.mediaclient",
    "com.spotify.music"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(onBack: () -> Unit, onPicked: (String) -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { InstalledApps.ensureLoaded(context) }
    val all by InstalledApps.apps.collectAsState()
    val loadingApps by InstalledApps.loading.collectAsState()
    val blocked by BlockerRepository.apps.collectAsState()

    var query by remember { mutableStateOf("") }

    val blockedPkgs = remember(blocked) { blocked.map { it.packageName }.toSet() }

    // Split into popular (installed + in POPULAR_PACKAGES) and the rest
    val installedPkgs = remember(all) { all.map { it.packageName }.toSet() }

    val popularApps = remember(all, query) {
        if (query.isNotBlank()) emptyList()
        else POPULAR_PACKAGES
            .filter { it in installedPkgs }
            .mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
    }

    val popularPkgSet = remember(popularApps) { popularApps.map { it.packageName }.toSet() }

    val filteredAll = remember(all, query, popularPkgSet) {
        val base = if (query.isBlank()) {
            all.filter { it.packageName !in popularPkgSet }
        } else {
            all.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        base
    }

    fun pick(device: DeviceApp) {
        BlockerRepository.upsertApp(
            BlockedApp(
                packageName = device.packageName,
                appName     = device.label,
                isReels     = InstalledApps.isReels(device.packageName),
                preset      = BlockPreset.FULLY_BLOCKED
            )
        )
        onPicked(device.packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose an app", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { InstalledApps.refresh(context) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh app list", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen, scrolledContainerColor = BgScreen)
            )
        },
        containerColor = BgScreen
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary) },
                singleLine  = true,
                modifier    = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentBlue,
                    unfocusedBorderColor = TextTertiary.copy(alpha = 0.3f)
                )
            )

            if (all.isEmpty() && loadingApps) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading apps…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                return@Scaffold
            }

            LazyColumn(Modifier.fillMaxSize()) {

                // ── Popular to block section ──────────────────────────────
                if (popularApps.isNotEmpty() && query.isBlank()) {
                    item {
                        SectionHeader(
                            title    = "Popular to block",
                            subtitle = "Apps most people add first"
                        )
                    }
                    items(popularApps, key = { "pop_${it.packageName}" }) { device ->
                        PickerRow(
                            device         = device,
                            alreadyBlocked = blockedPkgs.contains(device.packageName),
                            onPick         = { pick(device) }
                        )
                    }
                    item {
                        SectionHeader(
                            title    = "All apps",
                            subtitle = "Everything installed on your phone"
                        )
                    }
                }

                // ── Full list (or search results) ─────────────────────────
                items(filteredAll, key = { it.packageName }) { device ->
                    PickerRow(
                        device         = device,
                        alreadyBlocked = blockedPkgs.contains(device.packageName),
                        onPick         = { pick(device) }
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PICKER ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PickerRow(
    device: DeviceApp,
    alreadyBlocked: Boolean,
    onPick: () -> Unit
) {
    val icon = remember(device.packageName) { InstalledApps.iconFor(device.packageName) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !alreadyBlocked, onClick = onPick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIconOrLetter(icon = icon, label = device.label)

        Column(Modifier.weight(1f)) {
            Text(
                device.label,
                fontWeight = FontWeight.Medium,
                style      = MaterialTheme.typography.bodyLarge,
                color      = if (alreadyBlocked) TextSecondary else TextPrimary
            )
            Text(
                device.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                maxLines = 1
            )
        }

        if (alreadyBlocked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint     = AccentBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Added",
                    style  = MaterialTheme.typography.labelSmall,
                    color  = AccentBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color    = TextTertiary.copy(alpha = 0.10f)
    )
}
