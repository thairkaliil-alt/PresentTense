package com.allinone.blocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.data.LockdownGuard
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.data.UrlExtractor
import com.allinone.blocker.ui.InstalledApps
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppBlockerAccessibilityService : AccessibilityService() {

    private lateinit var overlay: OverlayManager

    private var currentForeground: String? = null
    private var sessionStart: Long = 0L

    // BUGFIX ("whitelisted app keeps getting closed every ~45s during
    // lockdown"): this scope used to be `CoroutineScope(Dispatchers.IO)`,
    // which — with no Job of its own — creates a single plain Job shared by
    // every coroutine launched on it. Under a plain Job, if ANY one of them
    // throws (e.g. ScreenTimeTracker hitting a transient database hiccup
    // while logging usage, which is unrelated to lockdown entirely), that
    // exception cancels the WHOLE scope — silently killing every other
    // coroutine sharing it, including lockdownGuardJob below, which is what
    // writes the "I'm still alive and enforcing" heartbeat every ~3s. Once
    // that heartbeat stops, LockdownWatchdogReceiver correctly (by its own
    // logic) concludes the enforcer looks dead ~45s later and drags the
    // user back to the lockdown screen — and since the underlying failure
    // keeps recurring, this repeats every ~45s, which is exactly the bug.
    // An uncaught exception here can also crash the whole app process.
    //
    // SupervisorJob() fixes the "one failure takes down everything else"
    // part: a failing child no longer cancels its siblings. The
    // CoroutineExceptionHandler fixes the "crashes the app" part: it logs
    // the failure instead of letting it propagate to Android's default
    // (process-crashing) handler. Together, a hiccup in something as minor
    // as screen-time logging can never again take the lockdown heartbeat
    // (or the whole app) down with it.
    private val ioExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("AppBlockerAccessibility", "Background task failed (isolated, not fatal)", throwable)
    }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + ioExceptionHandler)

    private val labelCache = HashMap<String, String>()
    private var lastOverlayShouldShow: Boolean? = null

    // ── Website-blocking state ───────────────────────────────────────────────
    private var currentDomain: String? = null
    private var lastUrlCheckAtMillis: Long = 0L
    private val MIN_URL_CHECK_GAP_MS = 400L

    // ── Reels detection state ────────────────────────────────────────────────
    // Tracks whether we last detected a Reels screen, so we only act when
    // the state actually changes (avoids hammering show/hide on every event).
    private var lastReelsState: Boolean? = null
    private var lastReelsCheckAtMillis: Long = 0L
    private val MIN_REELS_CHECK_GAP_MS = 300L

    /** Cached set of third-party HOME launcher packages (excludes our own app). */
    private var thirdPartyHomeLaunchers: Set<String>? = null

    /** Backstop loop that keeps a live lockdown session protected — see [tickLockdownGuard]. */
    private var lockdownGuardJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!BlockerRepository.isInitialized) BlockerRepository.init(applicationContext)
        if (!LockdownCompletionRepository.isInitialized) LockdownCompletionRepository.init(applicationContext)
        overlay = OverlayManager(this)
        // All real-time blocking (app blocks, lockdown, strict mode, reels
        // kill switch) happens right here in this accessibility service,
        // which Android keeps running without requiring any notification.
        // The one exception is lockdown sessions: those get an extra
        // notification + watchdog, started/stopped by the loop below, since
        // they're the one mode worth defending against the whole app being
        // killed (e.g. swiped out of Recent Apps) — see LockdownGuard.
        startLockdownGuardLoop()
    }

    /**
     * Runs for as long as this service is alive, at an ADAPTIVE pace:
     * every 3s while a lockdown session (or a break inside one) is
     * actually live — that's the only time there's anything to defend,
     * so it's the only time the extra wakeups are worth it — and every
     * 30s the rest of the time, just often enough to notice a *scheduled*
     * lockdown crossing its start time even if the user isn't touching
     * the phone (real-time protection for everything else is already
     * event-driven, via onAccessibilityEvent above — this loop is only
     * the backstop for the rare cases those events miss). This keeps the
     * background battery cost close to zero on an ordinary day where
     * lockdown never runs, instead of ticking fast 24/7.
     */
    private fun startLockdownGuardLoop() {
        lockdownGuardJob?.cancel()
        lockdownGuardJob = ioScope.launch {
            while (true) {
                val sessionLive = runCatching { tickLockdownGuard() }.getOrDefault(false)
                delay(if (sessionLive) 3_000L else 30_000L)
            }
        }
    }

    /** @return true if a lockdown session (active or on break) is currently live. */
    private fun tickLockdownGuard(): Boolean {
        val decision = LockdownEngine.evaluate(
            manualLockUntil = BlockerRepository.manualLockUntil.value,
            schedules = BlockerRepository.schedules.value
        )
        if (!decision.active && !decision.onBreak) {
            // Fastest of the several independent detection points (this
            // loop ticks every 3s while a session is live) — see
            // LockdownCompletionRepository's header comment for why it's
            // always safe to call this from more than one place.
            LockdownCompletionRepository.recordCompletionIfNeeded()
            LockdownGuard.ensureStopped(applicationContext)
            return false
        }

        LockdownGuard.ensureRunning(applicationContext)
        // Proof of life for LockdownWatchdogReceiver: as long as this loop is
        // ticking (every 3s while a session is live), the real enforcer is
        // up and already handling whitelisted apps correctly, so the
        // watchdog's 45s backstop should stand down instead of relaunching.
        BlockerRepository.recordLockdownHeartbeat()
        if (!decision.active) return true // on an emergency break — don't corral right now

        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePkg = activeRoot?.packageName?.toString()
        val activeClass = activeRoot?.className?.toString()
        activeRoot?.recycle()
        if (activePkg != null && shouldCorralDuringLockdown(activePkg, activeClass)) {
            corralToLockdownLauncher(activePkg)
        } else if (activePkg != null && activePkg != packageName) {
            // An allowed app (whitelisted / always-exempt) is genuinely in the
            // foreground — the lockdown overlay must not sit on top of it.
            // With the old Activity-based screen the permitted app naturally
            // covered it; an overlay window covers EVERYTHING, so it has to be
            // dismissed explicitly. No-op when it's already hidden.
            LockdownOverlay.hide()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        // Some devices only signal Home/recents via WINDOWS_CHANGED, with no
        // follow-up WINDOW_STATE_CHANGED for the launcher — check the active
        // window directly while lockdown is running.
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (!isLockdownActive()) return
            val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
            val activePkg = activeRoot?.packageName?.toString()
            val activeClass = activeRoot?.className?.toString()
            activeRoot?.recycle()
            if (activePkg != null && shouldCorralDuringLockdown(activePkg, activeClass)) {
                corralToLockdownLauncher(activePkg)
            } else if (activePkg != null && activePkg != packageName) {
                // Same as in tickLockdownGuard: an allowed app owns the screen,
                // so the overlay must come down off it.
                LockdownOverlay.hide()
            }
            return
        }

        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Our own windows: MainActivity is exempt from the generic lockdown
        // branch below, so corral it explicitly; the LockdownOverlay window
        // (which also reports our package) is left alone via the isShowing
        // check inside shouldCorralDuringLockdown.
        if (pkg == packageName) {
            if (className.contains("MainActivity")) {
                overlay.hide()
                currentForeground = pkg
                lastOverlayShouldShow = false
            }
            if (shouldCorralDuringLockdown(pkg, className)) {
                corralToLockdownLauncher(pkg)
            }
            return
        }

        // Home/recents are reported as System UI on most devices — never skip
        // these during lockdown or the user can escape via the Home button.
        if (pkg.contains("systemui", ignoreCase = true)) {
            if (shouldCorralDuringLockdown(pkg, className)) {
                corralToLockdownLauncher(pkg)
            }
            return
        }

        val appSwitched = pkg != currentForeground
        if (appSwitched) {
            currentForeground = pkg
            sessionStart = System.currentTimeMillis()
            lastOverlayShouldShow = null
            lastReelsState = null  // reset reels state on app switch
            if (currentDomain != null) {
                ioScope.launch { ScreenTimeTracker.onDomainChanged(applicationContext, null) }
                currentDomain = null
            }
            ioScope.launch { ScreenTimeTracker.reconcile(applicationContext) }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // IMPORTANT: this fires for every content change *inside* an app
            // that's already in the foreground (e.g. tapping deeper into a
            // settings screen) — appSwitched is false because the package
            // never changed. Lockdown corralling used to only run in the
            // "appSwitched" branch above, which meant: once you were already
            // sitting inside a non-whitelisted app (say, the phone's own
            // Settings, opened right as a schedule kicked in, or from before
            // lockdown even started), you could navigate freely deep inside
            // it — e.g. all the way to turning off this app's Accessibility
            // Service — and NOTHING here would kick you back out, since this
            // branch used to just `return` early. Only the ~10s background
            // watchdog loop (tickLockdownGuard) would eventually catch it.
            // Checking the corral here too closes that gap immediately,
            // event-by-event, instead of leaving up to ~10 seconds of free
            // navigation inside an already-open blocked app.
            if (shouldCorralDuringLockdown(pkg, className)) {
                corralToLockdownLauncher(pkg)
                return
            }
            if (pkg in UrlExtractor.BROWSER_PACKAGES) {
                handleBrowserContentChanged(pkg)
            }
            // ── Reels content-change detection ───────────────────────────────
            // When the user is already inside a reels-capable app and swipes
            // to the Reels tab (or away from it), we get CONTENT_CHANGED events
            // but NOT a WINDOW_STATE_CHANGED. So we must handle it here.
            if (BlockerRepository.reelsKillSwitch.value && InstalledApps.isReelsCapable(pkg)) {
                handleReelsContentChanged(pkg)
            }
            return
        } else if (lastOverlayShouldShow != null) {
            return
        }

        if (shouldCorralDuringLockdown(pkg, className)) {
            corralToLockdownLauncher(pkg)
            return
        }

        // Reaching here during an active lockdown means [pkg] is allowed
        // (whitelisted / always-exempt) — make sure the lockdown overlay
        // isn't sitting on top of it. No-op when already hidden.
        if (LockdownOverlay.isShowing && isLockdownActive()) {
            LockdownOverlay.hide()
        }

        if (pkg in UrlExtractor.BROWSER_PACKAGES) {
            handleBrowserContentChanged(pkg)
        }

        // ── Reels / Shorts kill switch ────────────────────────────────────────
        // Only block if the user is actually ON the Reels/Shorts screen,
        // not just because they opened Instagram or YouTube.
        // TikTok is always blocked (entire app is short-form).
        if (BlockerRepository.reelsKillSwitch.value && InstalledApps.isReelsCapable(pkg)) {
            handleReelsContentChanged(pkg)
            return
        }

        val app = BlockerRepository.appFor(pkg)
        if (app == null) {
            overlay.hide()
            lastOverlayShouldShow = false
            return
        }

        if (overlay.isShowing.not()) {
            BlockerRepository.recordOpen(pkg)
        }

        val decision = BlockEngine.evaluate(
            context = this,
            app = app,
            reelsKillSwitch = BlockerRepository.reelsKillSwitch.value,
            sessionStart = sessionStart
        )

        if (decision.blocked) {
            ioScope.launch { ScreenTimeTracker.recordBlockedAttempt(applicationContext, pkg) }
            overlay.show(
                packageName = pkg,
                appName = app.appName,
                reason = decision.reason,
                motivation = MOTIVATION,
                isLockdown = false
            )
            lastOverlayShouldShow = true
        } else {
            overlay.hide()
            lastOverlayShouldShow = false
            BlockerRepository.recordUse(pkg, System.currentTimeMillis())
        }
    }

    /**
     * Called when content changes inside a reels-capable app (Instagram,
     * YouTube, Facebook, Snapchat, TikTok). Inspects the accessibility node
     * tree to determine if the user is on a Reels/Shorts screen right now,
     * and shows or hides the overlay accordingly — WITHOUT blocking the
     * rest of the app.
     */
    private fun handleReelsContentChanged(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastReelsCheckAtMillis < MIN_REELS_CHECK_GAP_MS) return
        lastReelsCheckAtMillis = now

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val onReels = ReelsDetector.isOnReelsScreen(pkg, root)
        root?.recycle()

        // Only act when state changes — avoids hammering show/hide
        if (onReels == lastReelsState) return
        lastReelsState = onReels

        if (onReels) {
            ioScope.launch { ScreenTimeTracker.recordBlockedAttempt(applicationContext, pkg) }
            overlay.show(
                packageName = pkg,
                appName = appLabelOrPackage(pkg),
                reason = "Reels & Shorts kill switch is on",
                motivation = MOTIVATION,
                isLockdown = false  // user can dismiss and go back to feed
            )
            lastOverlayShouldShow = true
        } else {
            // User navigated away from Reels — let them browse freely
            overlay.hide()
            lastOverlayShouldShow = false
        }
    }

    private fun handleBrowserContentChanged(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastUrlCheckAtMillis < MIN_URL_CHECK_GAP_MS) return
        lastUrlCheckAtMillis = now

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val domain = UrlExtractor.extractDomain(pkg, root)
        root?.recycle()

        if (domain == currentDomain) return

        currentDomain = domain
        ioScope.launch { ScreenTimeTracker.onDomainChanged(applicationContext, domain) }

        if (domain == null) {
            if (lastOverlayShouldShow != true) overlay.hide()
            return
        }

        if (BlockerRepository.isWebsiteBlocked(domain)) {
            ioScope.launch { ScreenTimeTracker.recordDomainBlockedAttempt(applicationContext, domain) }
            overlay.show(
                packageName = pkg,
                appName = domain,
                reason = "This website is blocked",
                motivation = MOTIVATION,
                isLockdown = false
            )
            lastOverlayShouldShow = true
        } else {
            overlay.hide()
            lastOverlayShouldShow = false
        }
    }

    private fun appLabelOrPackage(pkg: String): String =
        labelCache.getOrPut(pkg) {
            runCatching {
                val pm = packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }

    private fun isLockdownActive(): Boolean = LockdownEngine.evaluate(
        manualLockUntil = BlockerRepository.manualLockUntil.value,
        schedules = BlockerRepository.schedules.value
    ).active

    /**
     * True when lockdown is active and [pkg] must not stay in the foreground.
     * Whitelisted apps are allowed, but home/recents surfaces and third-party
     * launchers are never allowed — they expose the whole device.
     */
    private fun shouldCorralDuringLockdown(pkg: String, className: String?): Boolean {
        if (!isLockdownActive()) return false

        if (pkg == packageName) {
            // Our own windows: the lockdown screen is now the LockdownOverlay
            // window (not an Activity with a recognizable class name), so
            // "showing" is the signal that the user is already corralled.
            // While it's up, everything of ours is covered by it — corralling
            // would just be a no-op show() plus a bogus blocked-attempt stat
            // every 3s guard tick. When it's NOT up (e.g. MainActivity in the
            // foreground mid-session), corral as before.
            return !LockdownOverlay.isShowing
        }

        if (pkg.contains("systemui", ignoreCase = true)) return true
        if (isThirdPartyHomeLauncher(pkg)) return true

        // The phone's own Settings app is a special case: it's how someone
        // would turn off this app's Accessibility Service or Device Admin —
        // i.e. the actual off switch for enforcement itself — so it must
        // never be reachable during lockdown, full stop. This check is
        // deliberately BEFORE the whitelist check, so it can't be
        // circumvented by whitelisting it (old installs may have it
        // whitelisted from before this existed — BlockerRepository also
        // refuses new whitelist entries for it, see addToWhitelist).
        if (LockdownEngine.isSystemSettingsPackage(pkg)) return true

        if (BlockerRepository.isWhitelisted(pkg)) return false
        if (LockdownEngine.isAlwaysExempt(this, pkg)) return false
        return true
    }

    private fun isThirdPartyHomeLauncher(pkg: String): Boolean {
        if (pkg == packageName) return false
        val launchers = thirdPartyHomeLaunchers ?: run {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
                .filter { it != packageName }
                .toSet()
                .also { thirdPartyHomeLaunchers = it }
        }
        return pkg in launchers
    }

    private fun corralToLockdownLauncher(blockedPkg: String) {
        ioScope.launch { ScreenTimeTracker.recordBlockedAttempt(applicationContext, blockedPkg) }
        overlay.hide()
        lastOverlayShouldShow = false
        LockdownOverlay.show(this)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (this::overlay.isInitialized) overlay.hide()
        // Cancel our own loop, but deliberately leave LockdownGuard's
        // notification/watchdog alarm running — those are what protect a
        // live session if this service is the thing getting killed.
        lockdownGuardJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MOTIVATION = "Discipline is choosing what you want most over what you want now."
    }
}
