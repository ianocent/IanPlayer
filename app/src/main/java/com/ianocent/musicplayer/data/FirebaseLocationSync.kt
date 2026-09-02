package com.ianocent.musicplayer.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import timber.log.Timber

class FirebaseLocationSync {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private var authRetryCount = 0
    private val maxAuthRetries = 3

    init {
        ensureAuthenticated()
    }

    private fun ensureAuthenticated() {
        if (auth.currentUser != null) {
            Timber.d("Firebase: already authenticated, uid=${auth.currentUser?.uid}")
            return
        }

        Timber.d("Firebase: attempting anonymous auth (attempt ${authRetryCount + 1}/$maxAuthRetries)")
        auth.signInAnonymously()
            .addOnSuccessListener {
                authRetryCount = 0
                Timber.d("Firebase: anonymous auth success, uid=${auth.currentUser?.uid}")
            }
            .addOnFailureListener { e ->
                authRetryCount++
                Timber.e("Firebase: anonymous auth failed (attempt $authRetryCount/$maxAuthRetries): ${e.message}")

                if (authRetryCount < maxAuthRetries) {
                    Timber.d("Firebase: will retry auth in 2s")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        ensureAuthenticated()
                    }, 2000)
                } else {
                    Timber.e("Firebase: auth retries exhausted, location sync will not work")
                }
            }
    }

    fun syncLocation(location: LocationData, songTitle: String, artist: String, userName: String = "") {
        val user = auth.currentUser
        if (user == null) {
            Timber.e("Firebase: syncLocation called but currentUser is null — auth may have failed")
            ensureAuthenticated()
            return
        }

        val data = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "city" to location.city,
            "country" to location.countryCode,
            "songTitle" to songTitle,
            "artist" to artist,
            "userName" to userName,
            "timestamp" to System.currentTimeMillis()
        )

        Timber.d("Firebase: writing location for user=${user.uid}, city=${location.city}, song=$songTitle")
        db.child("listeners").child(user.uid).setValue(data)
            .addOnSuccessListener { Timber.d("Firebase: location synced successfully") }
            .addOnFailureListener { Timber.e("Firebase: location sync failed: ${it.message}") }
    }

    fun clearLocation() {
        val userId = auth.currentUser?.uid ?: return
        Timber.d("Firebase: clearing location for user=$userId")
        db.child("listeners").child(userId).removeValue()
    }
}
