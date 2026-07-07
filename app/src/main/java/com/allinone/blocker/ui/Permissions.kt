package com.allinone.blocker.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
}
