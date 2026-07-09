package com.ianocent.musicplayer.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri,
    val album: String = "Unknown Album",
    val isStream: Boolean = false,
    val remoteArtUrl: String? = null,
    val dateAdded: Long = 0L
)