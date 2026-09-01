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
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300_000L)
            .setMinUpdateIntervalMillis(60_000L)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    try {
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        val countryCode = addresses?.firstOrNull()?.countryCode ?: "US"
                        val city = addresses?.firstOrNull()?.locality ?: "Unknown"

                        _location.value = LocationData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            countryCode = countryCode,
                            city = city
                        )
                        Timber.d("Location updated: $city, $countryCode")
                    } catch (e: Exception) {
                        Timber.e("Geocoder failed: ${e.message}")
                        _location.value = LocationData(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            countryCode = "US",
                            city = "Unknown"
                        )
                    }
                }
            }
        }

        locationCallback = callback
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Timber.d("Location tracking started")
    }

    fun stopTracking() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        Timber.d("Location tracking stopped")
    }
}
