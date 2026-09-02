package com.ianocent.musicplayer.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val countryCode: String,
    val city: String
)

class LocationTracker(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context, Locale.getDefault())

    private val _location = MutableStateFlow<LocationData?>(null)
    val location: StateFlow<LocationData?> = _location.asStateFlow()

    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startTracking() {
        Timber.d("LocationTracker: startTracking called")

        // Try last known location first
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Timber.d("LocationTracker: lastLocation found (${loc.latitude}, ${loc.longitude}), accuracy=${loc.accuracy}m")
                    if (_location.value == null) {
                        resolveAndSet(loc)
                    }
                } else {
                    Timber.d("LocationTracker: lastLocation is null")
                }
            }
            .addOnFailureListener { e ->
                Timber.w("LocationTracker: lastLocation failed: ${e.message}")
            }

        // Network-based location — works indoors, fast fix
        // Falls back to GPS when outdoor for better accuracy
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .setMaxUpdates(Int.MAX_VALUE)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc == null) {
                    Timber.w("LocationTracker: onLocationResult but lastLocation is null")
                    return
                }
                Timber.d("LocationTracker: got update (${loc.latitude}, ${loc.longitude}), accuracy=${loc.accuracy}m, provider=${loc.provider}")
                resolveAndSet(loc)
            }
        }

        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Timber.d("LocationTracker: requesting BALANCED location (network+GPS, interval=1min)")
    }

    private fun resolveAndSet(loc: android.location.Location) {
        try {
            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            val countryCode = addresses?.firstOrNull()?.countryCode ?: "ID"
            val city = addresses?.firstOrNull()?.locality
                ?: addresses?.firstOrNull()?.subAdminArea
                ?: "Unknown"

            _location.value = LocationData(
                latitude = loc.latitude,
                longitude = loc.longitude,
                countryCode = countryCode,
                city = city
            )
            Timber.d("LocationTracker: resolved — $city, $countryCode (${loc.latitude}, ${loc.longitude})")
        } catch (e: Exception) {
            Timber.e("LocationTracker: Geocoder failed: ${e.message}")
            _location.value = LocationData(
                latitude = loc.latitude,
                longitude = loc.longitude,
                countryCode = "ID",
                city = "Unknown"
            )
        }
    }

    fun stopTracking() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        Timber.d("Location tracking stopped")
    }
}
