package com.allinone.blocker.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.allinone.blocker.receiver.GeofenceBroadcastReceiver

/**
 * Thin wrapper around Android's [GeofencingClient].
 *
 * Call [sync] whenever the list of [LocationZone]s changes — it clears
 * all existing geofences and re-registers the current list. This keeps
 * the OS in sync with whatever the user has configured.
 */
object GeofenceManager {

    // 2-minute loitering delay before DWELL fires — avoids false positives
    // when the user just walks past a zone boundary.
    private const val LOITERING_DELAY_MS = 2 * 60 * 1000

    // Expiry: effectively never (100 years in ms)
    private const val NEVER_EXPIRE = 100L * 365 * 24 * 60 * 60 * 1000

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Re-registers all geofences to match [zones]. Safe to call with an
     * empty list — will just remove all existing fences.
     */
    @SuppressLint("MissingPermission")
    fun sync(context: Context, zones: List<LocationZone>) {
        val client: GeofencingClient = LocationServices.getGeofencingClient(context)
        val pi = pendingIntent(context)

        // Always clear first so we don't accumulate stale fences
        client.removeGeofences(pi).addOnCompleteListener {
            if (zones.isEmpty()) return@addOnCompleteListener

            // Need both fine location + background location to register geofences
            val hasFine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasBg = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFine || !hasBg) return@addOnCompleteListener

            val geofences = zones.map { zone ->
                Geofence.Builder()
                    .setRequestId(zone.id)
                    .setCircularRegion(zone.latitude, zone.longitude, zone.radiusMeters)
                    .setExpirationDuration(NEVER_EXPIRE)
                    .setLoiteringDelay(LOITERING_DELAY_MS)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_DWELL or
                        Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()
            }

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(
                    GeofencingRequest.INITIAL_TRIGGER_ENTER or
                    GeofencingRequest.INITIAL_TRIGGER_DWELL
                )
                .addGeofences(geofences)
                .build()

            client.addGeofences(request, pi)
        }
    }

    /** Call on app start to re-arm fences that survive across reboots. */
    fun rearm(context: Context) {
        val zones = BlockerRepository.strictMode.value.locationZones
        if (zones.isNotEmpty()) sync(context, zones)
    }
}
