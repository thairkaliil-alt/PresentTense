package com.allinone.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextSecondary
import java.util.Calendar

/** Minute presets shown as quick-pick chips. */
private val QUICK_ADD_PRESETS = listOf(5, 10, 15, 30, 60)

/**
 * Fast path for adding a Strict Alarm: instead of the full edit screen
 * (time wheel, repeat days, label, burst settings), the user just taps
 * how many minutes from now they want the alarm to go off, and it's
 * created + scheduled immediately as a one-shot (today-only) alarm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAlarmQuickAddScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    fun createAlarmInMinutes(minutesFromNow: Int) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutesFromNow)
        }
        val today = cal.get(Calendar.DAY_OF_WEEK)

        val entry = StrictAlarmEntry.newDefault(
            BlockerRepository.nextAlarmRequestCode()
        ).copy(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            // One-shot: only fires today, doesn't repeat every week.
            daysOfWeek = setOf(today),
            label = "Quick alarm"
        )

        BlockerRepository.addStrictAlarmEntry(entry)
        AlarmScheduler.schedule(context, entry)
        onDone()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Quick Add Alarm", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Set an alarm a few minutes from now — great for a quick break reminder.",
                color = TextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            QUICK_ADD_PRESETS.forEach { minutes ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(CardSurface, RoundedCornerShape(16.dp))
                        .pressable(onClick = { createAlarmInMinutes(minutes) })
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "In $minutes minutes",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Set",
                            color = AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
