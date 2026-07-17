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
    private const val LAUNCH_GRACE_MS = 3_000L

    val isShowing: Boolean get() = host != null

    /**
     * Brings the lockdown overlay up. Safe to call from any thread and as
     * often as you like — it hops to the main thread (WindowManager requires
     * it) and no-ops if the overlay is already showing or the app lacks the
     * "draw over other apps" permission.
     */
    fun show(context: Context) {
        val appContext = context.applicationContext
        runOnMain {
            if (host != null) return@runOnMain
            if (System.currentTimeMillis() < suppressShowUntilMillis) return@runOnMain
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
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
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
            // the launch transition briefly exposes the home screen underneath.
            suppressShowUntilMillis = System.currentTimeMillis() + LAUNCH_GRACE_MS
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
