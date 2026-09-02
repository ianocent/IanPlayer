package com.ianocent.musicplayer.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import timber.log.Timber

/**
 * Syncs social music signals to Firebase RTDB.
 *
 * RTDB structure:
 *   /signals/{uid}/{signalId}/
 *       rawText: "Tulus konser kemarin seru banget..."
 *       artist: "Tulus"
 *       title: "Hati-Hati di Jalan"
 *       contextKeywords: ["mood:happy", "activity:nongkrong", "genre:pop"]
 *       sourceApp: "com.instagram.android"
 *       timeOfDay: "evening"
 *       dayOfWeek: "friday"
 *       timestamp: Long
 */
class FirebaseSignalSync {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    /**
     * Writes a rich signal context to Firebase.
     */
    fun syncSignal(ctx: SignalContext) {
        val user = auth.currentUser
        if (user == null) {
            Timber.e("FirebaseSignalSync: currentUser is null — cannot sync signal")
            return
        }

        val data = mapOf(
            "rawText" to ctx.rawText,
            "artist" to ctx.artist,
            "title" to ctx.title,
            "contextKeywords" to ctx.contextKeywords,
            "sourceApp" to ctx.sourceApp,
            "timeOfDay" to ctx.timeOfDay,
            "dayOfWeek" to ctx.dayOfWeek,
            "timestamp" to ctx.timestamp
        )

        val key = db.child("signals").child(user.uid).push().key ?: return
        Timber.d("FirebaseSignalSync: writing signal for user=${user.uid}, artist=${ctx.artist}, title=${ctx.title}")
        db.child("signals").child(user.uid).child(key).setValue(data)
            .addOnSuccessListener { Timber.d("FirebaseSignalSync: signal synced") }
            .addOnFailureListener { Timber.e("FirebaseSignalSync: sync failed: ${it.message}") }
    }

    /**
     * Clears all signals for the current user from Firebase.
     */
    fun clearSignals() {
        val userId = auth.currentUser?.uid ?: return
        Timber.d("FirebaseSignalSync: clearing signals for user=$userId")
        db.child("signals").child(userId).removeValue()
    }
}
