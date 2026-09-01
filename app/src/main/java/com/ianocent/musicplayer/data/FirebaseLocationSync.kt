package com.ianocent.musicplayer.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import timber.log.Timber

class FirebaseLocationSync {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    init {
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { Timber.d("Anonymous auth success") }
                .addOnFailureListener { Timber.e("Anonymous auth failed: ${it.message}") }
        }
    }

    fun syncLocation(location: LocationData, songTitle: String, artist: String) {
        val userId = auth.currentUser?.uid ?: return
        val data = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "city" to location.city,
            "country" to location.countryCode,
            "songTitle" to songTitle,
            "artist" to artist,
            "timestamp" to System.currentTimeMillis()
        )

        db.child("listeners").child(userId).setValue(data)
            .addOnSuccessListener { Timber.d("Location synced to Firebase") }
            .addOnFailureListener { Timber.e("Firebase sync failed: ${it.message}") }
    }

    fun clearLocation() {
        val userId = auth.currentUser?.uid ?: return
        db.child("listeners").child(userId).removeValue()
    }
}
