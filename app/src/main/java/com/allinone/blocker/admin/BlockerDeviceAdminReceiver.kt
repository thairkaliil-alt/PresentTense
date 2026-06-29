package com.allinone.blocker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Device admin (README 5.5): being an active admin prevents the user from
 * uninstalling the app while a strict block is in effect.
 */
class BlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: android.content.Intent): CharSequence {
        return "Disabling admin removes uninstall protection while a block is active."
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, BlockerDeviceAdminReceiver::class.java)
    }
}
