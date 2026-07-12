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
    val dateAdded: Long = 0L,
    val remoteId: String? = null
)

data class AudioFormat(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val qualityLabel: String,
    val sizeBytes: Long = 0L
)

data class MonthlyRecap(
    val monthLabel: String,
    val totalPlays: Int,
    val totalMinutes: Long,
    val topSongs: List<Song>,
    val topArtists: List<Pair<String, Int>>,
    val topGenres: List<String>,
    val tasteComment: String
)

sealed class StreamSearchResult {
    data class Success(val songs: List<Song>) : StreamSearchResult()
    object Empty : StreamSearchResult()
    object ParsingFailed : StreamSearchResult()
}