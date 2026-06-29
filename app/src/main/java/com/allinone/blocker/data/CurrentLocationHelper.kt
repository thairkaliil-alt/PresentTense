package com.allinone.blocker.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Fetches the device's current GPS position for quick zone setup. */
object CurrentLocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context): Location? = suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)

        fun resumeWith(location: Location?) {
            if (cont.isActive) cont.resume(location)
        }

        client.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    resumeWith(cached)
                } else {
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setDurationMillis(15_000)
                        .setMaxUpdateAgeMillis(0)
                        .build()
                    client.getCurrentLocation(request, CancellationTokenSource().token)
                        .addOnSuccessListener { resumeWith(it) }
                        .addOnFailureListener { resumeWith(null) }
                }
            }
            .addOnFailureListener { resumeWith(null) }
    }
}
