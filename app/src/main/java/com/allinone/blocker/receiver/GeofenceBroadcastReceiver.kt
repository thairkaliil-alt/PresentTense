package com.allinone.blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.FrictionType

/**
 * Receives enter/exit events from the OS Geofencing API and updates
 * [BlockerRepository.strictMode] so [StrictModeGate] knows whether
 * the device is currently inside a location zone.
 *
 * This receiver is registered in the manifest so it works even when
 * the app is in the background or fully closed.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Make sure the repository is initialised (it's a no-op if already done)
        BlockerRepository.init(context.applicationContext)

        @Suppress("DEPRECATION")
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        val config = BlockerRepository.strictMode.value

        // Only act if Location Lock is actually enabled as a friction layer
        if (FrictionType.LOCATION_LOCK !in config.activeFrictions) return

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER,
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                // Device has entered (and lingered in) a zone — engage the lock
                if (!config.insideZone) {
                    BlockerRepository.setStrictMode(config.copy(insideZone = true))
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                // Device has left all zones — release the hard lock
                if (config.insideZone) {
                    BlockerRepository.setStrictMode(config.copy(insideZone = false))
                }
            }
        }
    }
}
