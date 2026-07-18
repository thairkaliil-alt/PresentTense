package com.allinone.blocker.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownCompletionRepository
import com.allinone.blocker.ui.LockdownLauncherScreen
import com.allinone.blocker.ui.MainActivity
import com.allinone.blocker.ui.theme.BlockerTheme

/**
 * The full-phone lockdown "home screen", drawn as a SYSTEM OVERLAY WINDOW
 * rather than as an Activity.
 *
 * WHY AN OVERLAY, NOT AN ACTIVITY — this is the whole point of the feature's
 * rearchitecture. The lockdown screen used to be [com.allinone.blocker.ui.LockdownLauncherActivity],
 * a normal Activity. An Activity is a task the OS can switch away from: pressing
 * Home genuinely brought the launcher to the front, and only *afterwards* did
 * the accessibility service notice and relaunch the lockdown Activity — the
 * visible "it goes away and comes back" flash. No amount of reacting faster
 * removes that gap, because the switch has already happened by the time we can
 * react.
 *
 * An [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] window is different:
 * it is painted ON TOP of whatever activity is in the foreground and is NOT
 * itself an activity/task. Pressing Home brings the real launcher forward
 * *underneath* this window — but the user never sees it, because this window
 * stays drawn on top the entire time. There is no task switch to flash past,
 * so the flash is gone at the source. This is the same mechanism [OverlayManager]
 * already uses for ordinary app-blocks, extended to host the full lockdown UI.
 *
 * The accessibility corral + watchdog (see [AppBlockerAccessibilityService],
 * [com.allinone.blocker.data.LockdownGuard]) are demoted to a pure backstop:
 * they just call [show] to make sure this overlay is up. Because [show] is
 * idempotent, those frequent calls are cheap no-ops while the overlay is
 * already visible.
 *
 * Tapping a whitelisted app [hide]s the overlay and launches the app normally;
 * when the user later leaves that app the accessibility service calls [show]
 * again. That single case is still reactive (the OS has to genuinely hand the
 * screen to the permitted app), but the lockdown screen itself — the thing the
 * user stares at for the whole session — never flashes again.
 */
@SuppressLint("StaticFieldLeak")
object LockdownOverlay {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** The single live overlay window, or null when nothing is shown. */
    private var host: Host? = null

    /**
     * While a whitelisted app is being launched from the lockdown screen,
     * [show] is suppressed until this deadline. Launching an app isn't
     * instant: the overlay comes down first, and for a beat the thing
     * visible (and reported to the accessibility service) is whatever was
     * underneath — usually the real home screen. Without this window the
     * corral sees that, "recovers" by re-showing the overlay, and the
     * overlay lands on top of the very app the user was just allowed to
     * open — the "whitelisted apps still get blocked" bug.
     */
    @Volatile
    private var suppressShowUntilMillis = 0L

    /**
     * The one package [suppressShowUntilMillis] is actually vouching for —
     * i.e. the whitelisted app someone just tapped from the lockdown
     * screen. See [isWithinLaunchGrace] for why this matters.
     */
    @Volatile
    private var suppressShowForPackage: String? = null
    private const val LAUNCH_GRACE_MS = 3_000L

    val isShowing: Boolean get() = host != null

    /**
     * Brings the lockdown overlay up. Safe to call from any thread and as
     * often as you like — it hops to the main thread (WindowManager requires
     * it) and no-ops if the overlay is already showing or the app lacks the
     * "draw over other apps" permission.
     *
     * @param foregroundPackage The package the caller has actually confirmed
     *   is in front right now, if it knows — used to decide whether the
     *   post-whitelisted-launch grace window still applies (see
     *   [isWithinLaunchGrace]). Leave null when the caller has no way to
     *   know what's currently in front (e.g. the watchdog alarm, or the
     *   HOME-intent fallback); the grace window never applies to those.
     * @param isHomeOrSystemSurface Whether [foregroundPackage] is a
     *   transient system surface (home launcher, status bar/recents) rather
     *   than a real app someone opened. Only relevant together with
     *   [foregroundPackage] — see [isWithinLaunchGrace].
     */
    fun show(context: Context, foregroundPackage: String? = null, isHomeOrSystemSurface: Boolean = false) {
        val appContext = context.applicationContext
        runOnMain {
            if (host != null) return@runOnMain
            if (isWithinLaunchGrace(foregroundPackage, isHomeOrSystemSurface)) return@runOnMain
            if (!Settings.canDrawOverlays(appContext)) return@runOnMain
            if (!BlockerRepository.isInitialized) BlockerRepository.init(appContext)
            if (!LockdownCompletionRepository.isInitialized) {
                LockdownCompletionRepository.init(appContext)
            }
            // Never let a failure to bring the overlay up crash the process: a
            // crash here, while a lockdown session stays active, would re-fire
            // on every relaunch and permanently brick the app. Fall back to the
            // accessibility corral (which will just try show() again) instead.
            runCatching { Host(appContext).also { it.attach() } }
                .onSuccess { host = it }
                .onFailure {
                    android.util.Log.e("LockdownOverlay", "Failed to show lockdown overlay", it)
                    host = null
                }
        }
    }

    /**
     * BUGFIX (lockdown bypass): the launch grace used to be a blanket "don't
     * show the overlay for 3 seconds, no matter what," armed the instant a
     * whitelisted app was tapped from the lockdown screen — and it didn't
     * care what actually showed up in front during those 3 seconds. That
     * meant: tap a whitelisted app, immediately press Home (the grace waves
     * the home screen through — see below), then within the same window
     * open Present Tense's own MainActivity, or a completely different
     * blocked app directly — and the lockdown screen would simply never
     * come back to stop it, because every re-show attempt during those 3
     * seconds was unconditionally swallowed.
     *
     * Fix: the grace now only rides for the ONE specific package it was
     * armed for ([suppressShowForPackage]) — or a transient system surface
     * ([isHomeOrSystemSurface]: the home launcher, status bar, recents)
     * that's structurally guaranteed to flash up for a moment while that
     * one app is still cold-starting. The instant anything else takes the
     * foreground — our own app's UI, or any other real app — the grace
     * ends right there even if time is still left on the clock, and the
     * very next corral attempt shows the overlay for real. A slow-starting
     * whitelisted app still gets the full benefit of the window (how long
     * it's allowed to take hasn't changed); what changed is that the
     * window can no longer be spent on anything else.
     *
     * Callers that don't know what's actually in the foreground (pass
     * `foregroundPackage = null`, the default) never qualify for the grace
     * at all — there's nothing to safely vouch for, so this always returns
     * false for them and [show] proceeds immediately.
     */
    private fun isWithinLaunchGrace(foregroundPackage: String?, isHomeOrSystemSurface: Boolean): Boolean {
        if (System.currentTimeMillis() >= suppressShowUntilMillis) return false
        val expectedPackage = suppressShowForPackage ?: return false
        if (foregroundPackage == null) return false
        if (foregroundPackage == expectedPackage) return true
        return isHomeOrSystemSurface
    }

    /** Tears the overlay down. Safe to call from any thread and when nothing is shown. */
    fun hide() {
        runOnMain {
            host?.detach()
            host = null
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    /**
     * Hosts a [ComposeView] in a WindowManager overlay. Compose needs a
     * lifecycle, a saved-state registry and a ViewModel store to run, which an
     * Activity normally supplies; since there's no Activity here we implement
     * those three owners ourselves — the standard pattern for driving Compose
     * from a raw window.
     */
    private class Host(private val context: Context) :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry get() = savedStateController.savedStateRegistry

        private val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        private val composeView = ComposeView(context)

        // The window's root view. ComposeView is final and can't be subclassed,
        // so the BACK-swallow lives here on the FrameLayout that hosts it —
        // there is no "leaving" the lockdown screen, same as the old Activity's
        // onBackPressed callback did.
        private val rootView = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
                return super.dispatchKeyEvent(event)
            }
        }

        fun attach() {
            // performAttach() MUST precede performRestore() on savedstate 1.2.0+
            // (performRestore throws otherwise). Both must run while the
            // lifecycle is still INITIALIZED, before we advance it below.
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED

            // The owners MUST be set on the view handed to WindowManager
            // (rootView), not on the ComposeView inside it: Compose's window
            // recomposer resolves ViewTreeLifecycleOwner from the WINDOW ROOT
            // view, and children find the owners by walking up the tree.
            // Setting them only on the child ComposeView crashes with
            // "ViewTreeLifecycleOwner not found" the moment the window attaches.
            rootView.setViewTreeLifecycleOwner(this)
            rootView.setViewTreeViewModelStoreOwner(this)
            rootView.setViewTreeSavedStateRegistryOwner(this)

            rootView.isFocusableInTouchMode = true
            rootView.addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            composeView.setContent {
                BlockerTheme(darkTheme = true) {
                    LockdownLauncherScreen(
                        onLaunchApp = ::launchApp,
                        onExitToApp = ::exitToApp,
                        onSessionComplete = ::sessionComplete
                    )
                }
            }

            val type =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            // Full-bleed and edge-to-edge: FLAG_LAYOUT_NO_LIMITS lets the window
            // paint under the status/nav bar areas so the lockdown surface reads
            // as one uninterrupted screen. The window is left focusable (no
            // FLAG_NOT_FOCUSABLE) so the BACK swallow above actually receives keys.
            //
            // FLAG_HARDWARE_ACCELERATED — THE FIX FOR THE LAGGY WHITELIST GRID:
            // windows added directly via WindowManager.addView() (as this one is)
            // do NOT inherit hardware acceleration the way a normal Activity window
            // does. android:hardwareAccelerated="true" in the manifest only covers
            // windows that belong to an Activity/Dialog; a raw overlay window like
            // this one silently falls back to software (CPU) rendering unless this
            // flag is set explicitly. Software rendering is fine for a static block
            // screen, but this window hosts a scrolling grid of app icons plus
            // continuously-animated gradients/rings (AmbientGlow, LockdownFocusRing)
            // — exactly the kind of content that's dramatically slower on the CPU,
            // which is what was showing up as severe scroll lag. This flag moves
            // the whole window onto the GPU, matching how every other (Activity-
            // based) screen in the app already renders.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE
            )
            // On Android 11+ the legacy LAYOUT_IN_SCREEN/NO_LIMITS flags above
            // are ignored and windows fit the system-bar insets by default —
            // which left the status/nav-bar strips uncovered, with the home
            // screen visible through them around the lockdown surface.
            // fitInsetsTypes = 0 is the modern way to say "cover everything".
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            runCatching { windowManager.addView(rootView, params) }
                .onSuccess { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
                .onFailure { lifecycleRegistry.currentState = Lifecycle.State.DESTROYED }
        }

        fun detach() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            runCatching { windowManager.removeView(rootView) }
            store.clear()
        }

        private fun launchApp(pkg: String) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Yield the screen to the permitted app: the overlay must come down
            // or it would cover the very app the user just chose to open. The
            // grace stamp keeps the corral from re-showing the overlay while
            // the launch transition briefly exposes the home screen underneath
            // — but only vouches for THIS package specifically, see
            // isWithinLaunchGrace's doc for why that scoping matters.
            suppressShowUntilMillis = System.currentTimeMillis() + LAUNCH_GRACE_MS
            suppressShowForPackage = pkg
            hide()
            runCatching { context.startActivity(intent) }
        }

        private fun exitToApp() {
            hide()
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { context.startActivity(intent) }
        }

        private fun sessionComplete() {
            LockdownCompletionRepository.recordCompletionIfNeeded()
            exitToApp()
        }
    }
}
