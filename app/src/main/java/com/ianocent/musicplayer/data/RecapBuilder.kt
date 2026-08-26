package com.ianocent.musicplayer.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure recap computation: turns a play-history window plus the song library
 * into a [MonthlyRecap]. No Android framework dependencies beyond formatting —
 * fully unit-testable through its single entry point.
 */
object RecapBuilder {

    /** Length of the recap window and the assumed minutes per play, matching legacy behaviour. */
    private const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    private const val MINUTES_PER_PLAY = 3.5

    /**
     * Builds a recap from play events (`songId to timestampMs`) restricted to
     * the 30-day window ending at [nowMs]. Returns null when nothing was played.
     */
    fun build(
        history: List<Pair<Long, Long>>,
        library: List<Song>,
        nowMs: Long
    ): MonthlyRecap? {
        val monthStart = nowMs - WINDOW_MS
        val windowed = history.filter { it.second >= monthStart }
        if (windowed.isEmpty()) return null

        val songPlayCounts = windowed.groupBy { it.first }.mapValues { it.value.size }
        val totalPlays = windowed.size

        val playedSongs = library.filter { songPlayCounts.containsKey(it.id) }
        val monthSongs = playedSongs
            .map { it to (songPlayCounts[it.id] ?: 0) }
            .sortedByDescending { it.second }

        val topSongs = monthSongs.take(5).map { it.first }

        val artistCounts = mutableMapOf<String, Int>()
        monthSongs.forEach { (song, count) ->
            val artistKey = song.artist.ifBlank { "Unknown Artist" }
            artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + count
        }
        val topArtists = artistCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        return MonthlyRecap(
            monthLabel = monthLabel(nowMs),
            totalPlays = totalPlays,
            totalMinutes = (totalPlays * MINUTES_PER_PLAY).toLong(),
            topSongs = topSongs,
            topArtists = topArtists,
            topGenres = emptyList(),
            tasteComment = generateTasteComment(topArtists, monthSongs)
        )
    }

    fun historyWindowStart(nowMs: Long): Long = nowMs - WINDOW_MS

    fun monthLabel(nowMs: Long): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(nowMs))

    private fun generateTasteComment(
        topArtists: List<Pair<String, Int>>,
        monthSongs: List<Pair<Song, Int>>
    ): String {
        if (topArtists.isEmpty()) return "Start listening to discover your music taste!"

        val artistNames = topArtists.take(3).map { it.first }
        val totalSongs = monthSongs.distinctBy { it.first.id }.size
        val topArtistName = artistNames.firstOrNull() ?: "music"

        val comments = listOf(
            "You've been vibing with $topArtistName a lot this month. Your taste is getting refined!",
            "$topArtistName is clearly your go-to artist right now. Solid choice!",
            "Your playlist is showing great variety with ${artistNames.joinToString(", ")}. Keep exploring!",
            "You discovered $totalSongs different songs this month. Your music journey is on fire!",
            "The vibes are strong this month! $topArtistName + ${artistNames.drop(1).firstOrNull() ?: "others"} = perfect combo.",
            "Your music taste is uniquely you. Love the energy from $topArtistName!",
            "What a month! You've been on a musical adventure with $totalSongs tracks. Respect the grind.",
            "$topArtistName has been your soundtrack this month. Iconic taste!"
        )

        return comments[topArtists.hashCode().rem(comments.size).let { if (it < 0) it + comments.size else it }]
    }
}
