package com.allinone.blocker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.data.StreakRepository
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.BlockerTheme
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.ThemePreference

// ─────────────────────────────────────────────────────────────────────────────
// SCREENS & NAVIGATION MODEL
// ─────────────────────────────────────────────────────────────────────────────

enum class Screen {
    HOME, PERMISSIONS, BLOCKED_APPS, BLOCKED_WEBSITES, APP_PICKER, APP_RULES,
    LOCKDOWN, WHITELIST, LOCKDOWN_SCHEDULES, EMERGENCY_BREAKS, STRICT_MODE, STATS, SETTINGS,
    STRICT_ALARM_LIST, STRICT_ALARM_EDIT, STRICT_ALARM_QUICK_ADD, SLEEP_CALCULATOR, PROFILE, STREAKS, POMODORO, PRESETS
}

data class NavTab(
    val screen: Screen,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
    val label: String
)

val bottomNavTabs = listOf(
    NavTab(Screen.LOCKDOWN,    Icons.Filled.LockClock, Icons.Outlined.LockClock, "Lockdown"),
    NavTab(Screen.STATS,       Icons.Filled.BarChart,  Icons.Outlined.BarChart,  "Stats"),
    NavTab(Screen.HOME,        Icons.Filled.Home,      Icons.Outlined.Home,      "Home"),
    NavTab(Screen.STRICT_MODE, Icons.Filled.Lock,      Icons.Outlined.Lock,      "Strict"),
    NavTab(Screen.PROFILE,     Icons.Filled.Person,    Icons.Outlined.Person,    "Profile")
)

val rootScreens = setOf(
    Screen.HOME, Screen.STRICT_MODE, Screen.STATS, Screen.LOCKDOWN, Screen.PROFILE
)

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val currentScreen = mutableStateOf(Screen.HOME)
    private val refresh = mutableStateOf(0)
    private lateinit var isDarkTheme: androidx.compose.runtime.MutableState<Boolean>
    // Whether the first-run permission walkthrough (OnboardingScreen.kt) has
    // already been completed. Read synchronously here — same pattern as
    // isDarkTheme just below — so the very first frame Compose draws already
    // knows whether to show onboarding or the normal Home screen, instead of
    // flashing one and then swapping to the other.
    private lateinit var onboardingComplete: androidx.compose.runtime.MutableState<Boolean>
    private var backPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BlockerRepository.init(this)
        LockdownCompletionRepository.init(this)
        // BlockerForegroundService (and its persistent notification) is no
        // longer started here. Blocking runs entirely off the accessibility
        // service, which needs no notification — see
        // AppBlockerAccessibilityService.onServiceConnected() for details.

        isDarkTheme = mutableStateOf(ThemePreference.isDarkMode(this))
        onboardingComplete = mutableStateOf(OnboardingPreference.isComplete(this))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val screen = currentScreen.value

                if (StrictModeGate.pendingAction.value != null) {
                    StrictModeGate.cancel()
                    return
                }

                when (screen) {
                    Screen.HOME -> {
                        if (backPressedOnce) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        } else {
                            backPressedOnce = true
                            Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                            android.os.Handler(mainLooper).postDelayed({ backPressedOnce = false }, 2_000)
                        }
                    }
                    Screen.BLOCKED_APPS     -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    Screen.BLOCKED_WEBSITES -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    Screen.APP_PICKER       -> { currentScreen.value = Screen.BLOCKED_APPS;     backPressedOnce = false }
                    Screen.APP_RULES        -> { currentScreen.value = Screen.BLOCKED_APPS;     backPressedOnce = false }
                    Screen.WHITELIST        -> { currentScreen.value = Screen.LOCKDOWN;         backPressedOnce = false }
                    Screen.LOCKDOWN_SCHEDULES -> { currentScreen.value = Screen.LOCKDOWN;       backPressedOnce = false }
                    Screen.EMERGENCY_BREAKS -> { currentScreen.value = Screen.LOCKDOWN;         backPressedOnce = false }
                    Screen.STRICT_ALARM_LIST -> { currentScreen.value = Screen.HOME;            backPressedOnce = false }
                    Screen.STRICT_ALARM_EDIT -> { currentScreen.value = Screen.STRICT_ALARM_LIST; backPressedOnce = false }
                    Screen.SLEEP_CALCULATOR -> { currentScreen.value = Screen.STRICT_ALARM_LIST; backPressedOnce = false }
                    Screen.STREAKS          -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    Screen.POMODORO         -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    Screen.PRESETS          -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    Screen.PERMISSIONS      -> { currentScreen.value = Screen.SETTINGS;         backPressedOnce = false }
                    Screen.SETTINGS         -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    Screen.PROFILE          -> { currentScreen.value = Screen.HOME;             backPressedOnce = false }
                    else -> { currentScreen.value = Screen.HOME; backPressedOnce = false }
                }
            }
        })

        setContent {
            BlockerTheme(darkTheme = isDarkTheme.value) {
                AppRoot(
                    screenState = currentScreen,
                    refreshKey = refresh.value,
                    isDarkTheme = isDarkTheme.value,
                    onThemeToggle = { newValue ->
                        isDarkTheme.value = newValue
                        ThemePreference.setDarkMode(this, newValue)
                    },
                    onboardingCompleteState = onboardingComplete
                )
            }
        }
    }

  override fun onResume() {
        super.onResume()
        refresh.value++
        backPressedOnce = false
        StreakRepository.evaluateFinishedDays(this)
        enforceLockdown()
    }

    /**
     * During an active lockdown the blocker's own UI must not become an escape
     * hatch: exiting a whitelisted app unwinds the back stack to MainActivity,
     * and the accessibility service exempts our own package — so without this we
     * would land here and the lockdown would appear to have ended. Bounce back to
     * the lockdown launcher instead. The one exception is while a strict-unlock
     * challenge is pending, which is the sanctioned path to actually end early.
     */
    private fun enforceLockdown() {
        if (StrictModeGate.pendingAction.value != null) return
        val active = com.allinone.blocker.data.LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        ).active
        if (active) LockdownLauncherActivity.launch(this)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// APP ROOT — routing logic
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppRoot(
    screenState: androidx.compose.runtime.MutableState<Screen>,
    refreshKey: Int,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onboardingCompleteState: androidx.compose.runtime.MutableState<Boolean>
) {
    var screen by screenState
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var isNewApp        by remember { mutableStateOf(false) }
    var selectedAlarmId by remember { mutableStateOf<String?>(null) }
    var isNewAlarm      by remember { mutableStateOf(false) }
    var justAddedAlarms by remember { mutableStateOf<List<com.allinone.blocker.data.StrictAlarmEntry>?>(null) }
    var justSavedAlarm  by remember { mutableStateOf<com.allinone.blocker.data.StrictAlarmEntry?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // ── FIRST-RUN ONBOARDING ─────────────────────────────────────────────
    // Takes over the whole screen before anything else — including the
    // unlock-challenge and lockdown-completion takeovers below — since a
    // brand-new install can't be mid-lockdown or mid-unlock-challenge yet.
    // Checked first every recomposition, so the moment OnboardingScreen
    // calls onFinish() and flips this to true, the very next frame shows
    // the normal app instead.
    if (!onboardingCompleteState.value) {
        OnboardingScreen(onFinish = { onboardingCompleteState.value = true })
        return
    }

    // ── LOCKDOWN GATE for Permissions ────────────────────────────────────
    // The Permissions screen is where Accessibility / Overlay / Usage
    // Access / Device Admin can all be switched off — i.e. the actual
    // levers that would cripple enforcement. It's the one screen locked
    // out entirely while a lockdown session is active (extend
    // [lockdownLockedScreens] if Strict Mode should do the same later).
    //
    // Two layers, so this can't be cheated around either direction:
    //   1. goTo() is how Permissions is opened from anywhere in this file —
    //      it re-checks live, with the real current time, at the moment of
    //      the tap. A stale "not locked yet" read isn't possible.
    //   2. The eviction LaunchedEffect further down handles the case where
    //      a *scheduled* lockdown kicks in while the user is already
    //      sitting on Permissions — it ticks every second while that
    //      screen is open and boots them out the instant the schedule
    //      flips on, instead of waiting for their next tap or app resume.
    val lockdownLockedScreens = setOf(Screen.PERMISSIONS)

    fun isLockedRightNow(target: Screen): Boolean {
        if (target !in lockdownLockedScreens) return false
        return com.allinone.blocker.data.LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value,
            breakUntilMillis = BlockerRepository.breakUntil.value
        ).active
    }

    fun goTo(target: Screen) {
        if (isLockedRightNow(target)) {
            Toast.makeText(
                context,
                "Permissions are locked during your lockdown session",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        screen = target
    }

    // Only ticks while the user is actually sitting on a locked-during-
    // lockdown screen — idle the rest of the time, so this costs nothing
    // in the common case.
    LaunchedEffect(screen) {
        while (screen in lockdownLockedScreens) {
            if (isLockedRightNow(screen)) {
                Toast.makeText(
                    context,
                    "Permissions are locked during your lockdown session",
                    Toast.LENGTH_SHORT
                ).show()
                screen = Screen.SETTINGS
                break
            }
            kotlinx.coroutines.delay(1_000)
        }
    }

    val pendingAction by StrictModeGate.pendingAction.collectAsState()
    val strictConfig  by BlockerRepository.strictMode.collectAsState()

    if (pendingAction != null) {
        UnlockChallengeScreen(
            config = strictConfig,
            onSuccess = { StrictModeGate.confirm() },
            onCancel  = { StrictModeGate.cancel()  }
        )
        return
    }

    // A completed lockdown session takes over the screen exactly the same
    // way — a real, earned moment (see LockdownCompletionRepository.kt)
    // deserves the same full-takeover treatment as the unlock challenge
    // above, not a small toast buried under whatever screen happens to be
    // showing. Checked before routing so it intercepts regardless of which
    // screen/sub-screen was about to be shown.
    val pendingCelebration by LockdownCompletionRepository.pendingCelebration.collectAsState()
    pendingCelebration?.let { session ->
        LockdownCompletionScreen(
            session = session,
            onDismiss = { LockdownCompletionRepository.consumePendingCelebration() }
        )
        return
    }

    // Sub-screens are full-screen "pushed" destinations (no bottom nav). Routing
    // them through one ScreenPush gives every push/pop a directional slide so the
    // navigation actually reads as movement, instead of a hard cut.
  val subScreens = setOf(
        Screen.BLOCKED_APPS, Screen.BLOCKED_WEBSITES, Screen.APP_PICKER, Screen.APP_RULES,
        Screen.WHITELIST, Screen.LOCKDOWN_SCHEDULES, Screen.EMERGENCY_BREAKS,
        Screen.STRICT_ALARM_LIST, Screen.STRICT_ALARM_EDIT, Screen.STRICT_ALARM_QUICK_ADD,
        Screen.SLEEP_CALCULATOR, Screen.PERMISSIONS, Screen.SETTINGS, Screen.STREAKS, Screen.POMODORO, Screen.PRESETS
    )
    if (screen in subScreens) {
        com.allinone.blocker.ui.motion.ScreenPush(targetState = screen) { current ->
            when (current) {
                Screen.BLOCKED_APPS -> BlockedAppsScreen(
                    onBack = { screen = Screen.HOME },
                    onAdd  = { screen = Screen.APP_PICKER },
                    onEdit = { pkg -> selectedPackage = pkg; isNewApp = false; screen = Screen.APP_RULES }
                )
                Screen.BLOCKED_WEBSITES -> BlockedWebsitesScreen(onBack = { screen = Screen.HOME })
                Screen.APP_PICKER -> AppPickerScreen(
                    onBack   = { screen = Screen.BLOCKED_APPS },
                    onPicked = { pkg -> selectedPackage = pkg; isNewApp = true; screen = Screen.APP_RULES }
                )
                Screen.APP_RULES -> AppRulesScreen(
                    packageName = selectedPackage,
                    isNew       = isNewApp,
                    onBack      = { screen = Screen.BLOCKED_APPS },
                    onOpenStrictMode = { screen = Screen.STRICT_MODE }
                )
                Screen.WHITELIST -> WhitelistScreen(onBack = { screen = Screen.LOCKDOWN })
                Screen.LOCKDOWN_SCHEDULES -> LockdownSchedulesScreen(onBack = { screen = Screen.LOCKDOWN })
                Screen.EMERGENCY_BREAKS   -> EmergencyBreaksScreen(onBack = { screen = Screen.LOCKDOWN })
        Screen.STRICT_ALARM_LIST -> StrictAlarmListScreen(
    onBack = { screen = Screen.HOME },
    onAddAlarm = {
        val fresh = com.allinone.blocker.data.StrictAlarmEntry.newDefault(
            BlockerRepository.nextAlarmRequestCode()
        )
        selectedAlarmId = fresh.id
        isNewAlarm = true
        screen = Screen.STRICT_ALARM_EDIT
    },
    onQuickAdd = { screen = Screen.STRICT_ALARM_QUICK_ADD },
    onEditAlarm = { alarmId ->
        selectedAlarmId = alarmId
        isNewAlarm = false
        screen = Screen.STRICT_ALARM_EDIT
    },
    onOpenSleepCalculator = { screen = Screen.SLEEP_CALCULATOR },
    justAddedAlarms = justAddedAlarms,
    onJustAddedAlarmsConsumed = { justAddedAlarms = null },
    justSavedAlarm = justSavedAlarm,
    onJustSavedAlarmConsumed = { justSavedAlarm = null }
)
Screen.STRICT_ALARM_QUICK_ADD -> StrictAlarmQuickAddScreen(
    onBack = { screen = Screen.STRICT_ALARM_LIST },
    onDone = { createdEntries ->
        justAddedAlarms = createdEntries
        screen = Screen.STRICT_ALARM_LIST
    }
)
Screen.STRICT_ALARM_EDIT -> StrictAlarmEditScreen(
    alarmId = selectedAlarmId ?: com.allinone.blocker.data.StrictAlarmEntry.newDefault(
        BlockerRepository.nextAlarmRequestCode()
    ).id,
    isNew = isNewAlarm,
    onBack = { screen = Screen.STRICT_ALARM_LIST },
    onSaved = { saved ->
        justSavedAlarm = saved
        screen = Screen.STRICT_ALARM_LIST
    },
    onDelete = { alarmId ->
        val alarmToDelete = BlockerRepository.strictAlarms.value.firstOrNull { it.id == alarmId }
        BlockerRepository.removeStrictAlarmEntry(alarmId)
        if (alarmToDelete != null) {
            com.allinone.blocker.data.AlarmScheduler.cancel(context, alarmToDelete)
        } else {
            com.allinone.blocker.data.AlarmScheduler.cancel(context, alarmId)
        }
        screen = Screen.STRICT_ALARM_LIST
    }
)
                Screen.SLEEP_CALCULATOR -> SleepCalculatorScreen(onBack = { screen = Screen.STRICT_ALARM_LIST })
                Screen.PERMISSIONS -> PermissionsScreen(refreshKey = refreshKey, onBack = { screen = Screen.SETTINGS })
                Screen.SETTINGS -> SettingsScreen(
                    refreshKey = refreshKey,
                    onBack = { screen = Screen.HOME },
                    onPermissions = { goTo(Screen.PERMISSIONS) }
                )
                Screen.STREAKS -> StreaksScreen(onBack = { screen = Screen.HOME })
                Screen.POMODORO -> PomodoroScreen(onBack = { screen = Screen.HOME })
                Screen.PRESETS -> PresetsScreen(onBack = { screen = Screen.HOME })
                else -> { /* root screens render in the scaffold below */ }
            }
        }
        return
    }

    // This Box is the true full screen — the same one that also holds the
    // bottom tab bar below. Wrapping the root Scaffold in it (instead of
    // just using the Scaffold on its own) gives us a place to paint the
    // lockdown "void" overlay ON TOP of the tab bar, rather than being
    // boxed into whatever slot the Scaffold hands its content. See
    // [LockdownVoidOverlayState] for why this is needed.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = com.allinone.blocker.ui.theme.BgScreen,
            bottomBar = {
                AppBottomNav(
                    currentScreen = screen,
                    onTabSelected = { screen = it }
                )
            }
        ) { innerPadding ->
            // Cross-fade between root tabs so switching tabs feels designed, not cut.
            com.allinone.blocker.ui.motion.ScreenSwitch(targetState = screen) { current ->
            when (current) {
                Screen.HOME -> HomeScreen(
                    refreshKey            = refreshKey,
                    // NOTE: HomeScreen currently never calls this itself (no
                    // permissions button on Home yet), so in practice
                    // Permissions is only ever opened from Settings right now.
                    // Routed through goTo() anyway so it's covered by the
                    // lockdown gate the moment a Home shortcut is added.
                    onPermissions         = { goTo(Screen.PERMISSIONS) },
                    onOpenBlockedApps     = { screen = Screen.BLOCKED_APPS },
                    onOpenBlockedWebsites = { screen = Screen.BLOCKED_WEBSITES },
                    onOpenPresets         = { screen = Screen.PRESETS },
                    onSettings            = { screen = Screen.SETTINGS },
                    onAlarmClick          = { screen = Screen.STRICT_ALARM_LIST },
                    onPomodoroClick       = { screen = Screen.POMODORO },
                    onOpenStreaks         = { screen = Screen.STREAKS },
                    onOpenStats           = { screen = Screen.STATS },
                    isDarkTheme           = isDarkTheme,
                    onThemeToggle         = onThemeToggle
                )
                Screen.STRICT_MODE -> StrictModeSettingsScreen(onBack = { screen = Screen.HOME })
                Screen.STATS       -> StatsScreen(onBack = { screen = Screen.HOME }, onOpenStreaks = { screen = Screen.STREAKS })
                Screen.LOCKDOWN    -> LockdownScreen(
                    onBack                  = { screen = Screen.HOME },
                    onManageWhitelist       = { screen = Screen.WHITELIST },
                    onManageSchedules       = { screen = Screen.LOCKDOWN_SCHEDULES },
                    onManageEmergencyBreaks = { screen = Screen.EMERGENCY_BREAKS }
                )
                Screen.PROFILE     -> ProfileScreen(onOpenSettings = { screen = Screen.SETTINGS })
                else -> { /* sub-screens handled above */ }
            }
            }
        }

        // The hold-driven lockdown void, mirrored live from LockdownScreen via
        // [LockdownVoidOverlayState]. Painted as a sibling of the Scaffold
        // above (not inside its content slot), so it grows over the ENTIRE
        // screen — bottom tab bar included — instead of stopping at the
        // Lockdown tab's own content area.
        LockdownVoidOverlayState.origin?.let { origin ->
            // Reading .value right here, in this one small block, is what
            // keeps this 60fps-during-a-hold read scoped to just this
            // overlay — not the Scaffold/tab content above it.
            val progress = LockdownVoidOverlayState.progressState?.value ?: 0f
            VoidExpansion(origin = origin, progress = progress)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROFILE PLACEHOLDER SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfilePlaceholderScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM NAVIGATION BAR
// ─────────────────────────────────────────────────────────────────────────────

private val BarHeight       = 72.dp
private val HomeBubbleSize  = 60.dp
private val HomeBubbleRaise = 14.dp
private val SideIconSize    = 22.dp
private val HomeIconSize    = 28.dp

@Composable
fun AppBottomNav(
    currentScreen: Screen,
    onTabSelected: (Screen) -> Unit
) {
    Surface(
        color = BgDarkest,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight + HomeBubbleRaise)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavTabs.forEach { tab ->
                    val selected = currentScreen == tab.screen
                    if (tab.screen == Screen.HOME) {
                        HomeTab(
                            tab      = tab,
                            selected = selected,
                            onClick  = { onTabSelected(tab.screen) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        SideTab(
                            tab      = tab,
                            selected = selected,
                            onClick  = { onTabSelected(tab.screen) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideTab(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "sideTabSelectionScale"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "sideTabPressScale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) AccentBlue else TextSecondary,
        animationSpec = tween(180),
        label = "sideTabColor"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 16.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "pillWidth"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) tab.iconFilled else tab.iconOutlined,
            contentDescription = tab.label,
            tint = iconColor,
            modifier = Modifier
                .size(SideIconSize)
                .scale(selectionScale * pressScale)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AccentBlue)
        )
    }
}

@Composable
private fun HomeTab(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val raise by animateDpAsState(
        targetValue = if (selected) HomeBubbleRaise else HomeBubbleRaise - 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "homeRaise"
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "homeScale"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "homePressScale"
    )
    val bubbleColor by animateColorAsState(
        targetValue = if (selected) AccentBlue else BgDarkest,
        animationSpec = tween(200),
        label = "homeBubbleColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) Color.White else AccentBlue.copy(alpha = 0.7f),
        animationSpec = tween(200),
        label = "homeIconColor"
    )

    Column(
        modifier = modifier
            .height(BarHeight + HomeBubbleRaise)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Surface(
            shape           = CircleShape,
            color           = bubbleColor,
            shadowElevation = if (selected) 8.dp else 3.dp,
            modifier = Modifier
                .offset(y = -raise)
                .size(HomeBubbleSize)
                .scale(selectionScale * pressScale)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = if (selected) tab.iconFilled else tab.iconOutlined,
                    contentDescription = tab.label,
                    tint               = iconColor,
                    modifier           = Modifier.size(HomeIconSize)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text       = tab.label,
            fontSize   = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (selected) AccentBlue else TextSecondary,
            maxLines   = 1,
            overflow   = TextOverflow.Clip,
            modifier   = Modifier.padding(bottom = 8.dp)
        )
    }
}
