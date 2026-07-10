package com.allinone.blocker.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.allinone.blocker.admin.BlockerDeviceAdminReceiver
import com.allinone.blocker.service.AppBlockerAccessibilityService

/** Helpers to check and request the permissions the blocker depends on (README 12). */
object Permissions {

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** "Allow while using the app" — step 1. Required before background can even be asked for. */
    fun hasForegroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** "Allow all the time" — step 2. This is the one Location Lock actually needs to work
     *  when the app isn't open, since geofence events fire in the background. */
    fun hasBackgroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** True only once both steps are done and Location Lock can actually fire. */
    fun hasFullLocationAccess(context: Context): Boolean =
        hasForegroundLocation(context) && hasBackgroundLocation(context)

    /** On Android 10+, "Allow all the time" can't be granted from the normal permission
     *  popup — the user has to flip it on from the app's own system settings page. */
    fun openAppLocationSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasAccessibility(context: Context): Boolean {
        val expected = context.packageName + "/" +
            AppBlockerAccessibilityService::class.java.name
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * True once the user has made Present Tense an active Device
     * Administrator. This is what actually blocks uninstalling the app: on
     * stock Android, an app can't be uninstalled (from Settings or the Play
     * Store) while it's an active device admin — the user is forced to
     * deactivate it first, which routes back through the Settings app and
     * is therefore itself blocked while a lockdown session is live (see
     * AppBlockerAccessibilityService.shouldCorralDuringLockdown).
     */
    fun hasDeviceAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as? DevicePolicyManager ?: return false
        return dpm.isAdminActive(BlockerDeviceAdminReceiver.componentName(context))
    }

    /**
     * Opens Android's built-in "Activate device admin app?" screen for
     * Present Tense. There's no code-only way to grant this — same as
     * Accessibility, the user has to confirm it themselves on that system
     * screen.
     */
    fun requestDeviceAdmin(context: Context) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                BlockerDeviceAdminReceiver.componentName(context)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops Present Tense from being uninstalled as a shortcut around a block."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * True once Present Tense is the device's ACTIVE default Home app —
     * the thing that actually opens when Home is pressed. The app manifest
     * registers LockdownLauncherActivity as *a* HOME option automatically,
     * but that's not the same as being the chosen one: until the user picks
     * it in Settings, pressing Home still opens their normal launcher
     * (Pixel Launcher, One UI Home, etc.) first, and Present Tense only
     * gets a chance to react a beat later via the accessibility service.
     * Setting it as default Home removes that gap at the source, for the
     * case where Home is pressed from inside an already-open whitelisted
     * app (screen pinning, see LockdownLauncherActivity, handles the case
     * where Home is pressed from the lockdown screen itself).
     */
    fun isDefaultHomeApp(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            homeIntent, PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Opens Android's "Home app" picker so the user can choose Present Tense. */
    fun openHomeAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * True once the user has flipped on Android's own "Screen pinning"
     * toggle (Settings > Security > Advanced > Screen pinning, wording
     * varies by OEM). This is what LockdownLauncherActivity relies on to
     * call Activity.startLockTask() — without Device Owner, an app can
     * only pin itself (disabling Home/Recents at the OS level) if the user
     * has enabled this system setting first. There's no public API to turn
     * it on for them; if it's off, startLockTask() silently fails and
     * Android shows its own "screen pinning isn't turned on" system
     * message instead. `lock_to_app_enabled` isn't part of the public SDK
     * as a named constant, but it's the stable, long-standing key Android
     * itself stores this toggle under.
     */
    fun hasScreenPinningEnabled(context: Context): Boolean =
        runCatching {
            Settings.System.getInt(context.contentResolver, "lock_to_app_enabled", 0) == 1
        }.getOrDefault(false)

    /**
     * Opens Security settings — the closest universal deep link across
     * OEMs. There's no public Intent action that jumps straight to the
     * Screen Pinning toggle itself (it lives a couple of taps deeper, under
     * "Advanced" or "More security settings" depending on the device), so
     * this gets the user to the right general screen and the in-app prompt
     * tells them what to tap next.
     */
    fun openScreenPinningSettings(context: Context) {
        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * True once Android's OWN battery optimizer ("Doze") has been told to
     * leave Present Tense unrestricted. This doesn't fix any specific bug —
     * it's a general resilience measure that makes the rare "phone kills
     * the whole app process in the background" scenario rarer, which is
     * exactly the case LockdownWatchdogReceiver's heartbeat check exists to
     * recover from (see its header comment).
     */
    fun hasBatteryOptimizationExemption(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens Android's own direct "Allow Present Tense to ignore battery
     * optimizations?" dialog — one tap for the user, no hunting through
     * Settings menus. Requires the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     * manifest permission (declared in AndroidManifest.xml) to be allowed
     * to show this dialog at all.
     */
    fun requestBatteryOptimizationExemption(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Several phone brands (Xiaomi, Huawei, OPPO, Vivo, OnePlus, Samsung)
     * ship their OWN battery/process manager on top of stock Android's,
     * with a separate "autostart" / "allow background activity" toggle that
     * [requestBatteryOptimizationExemption] above does NOT reach — and
     * Android has no public API to check or flip these, so unlike every
     * other permission in this file there's no "granted" state to detect,
     * only a best-effort deep link into the right screen. This tries each
     * known manufacturer screen in turn — skipping any that don't exist on
     * this device — and falls back to this app's own system details page
     * (which always exists) if none of them do.
     */
    fun openBackgroundAutostartSettings(context: Context) {
        val candidates = listOf(
            // Xiaomi / MIUI / HyperOS
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Huawei / EMUI / HarmonyOS
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            // OPPO / ColorOS (two package names used across OS versions)
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            // Vivo / Funtouch OS / OriginOS
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // OnePlus / OxygenOS / ColorOS (post-merger OnePlus devices)
            ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            ),
            // Samsung / One UI — Device Care's own battery screen
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        )

        val pm = context.packageManager
        val opened = candidates
            .map { Intent().setComponent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .filter { it.resolveActivity(pm) != null }
            .any { intent -> runCatching { context.startActivity(intent) }.isSuccess }

        if (!opened) {
            // No known manufacturer screen exists on this device (or all
            // failed to launch) — fall back to this app's own system
            // details page. On phones without a separate autostart manager
            // (stock Android, Pixel, most non-Chinese-OEM devices) this is
            // also where any equivalent background-activity toggle lives.
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
