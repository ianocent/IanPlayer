package com.ianocent.musicplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RecapBuilder through its interface: history window in, MonthlyRecap out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecapBuilderTest {

    private val nowMs = 1_700_000_000_000L

    private fun song(id: Long, artist: String = "Artist A"): Song =
        Song(
            id = id,
            title = "Song $id",
            artist = artist,
            duration = 0L,
            uri = Uri.parse("content://test/$id"),
            isStream = false
        )

    private fun play(id: Long, ageDaysAgo: Long) =
        Pair(id, nowMs - ageDaysAgo * 24L * 60 * 60 * 1000)

    @Test
    fun emptyHistoryYieldsNull() {
        assertNull(RecapBuilder.build(emptyList(), listOf(song(1)), nowMs))
    }

    @Test
    fun playsOutsideTheWindowAreIgnored() {
        val history = listOf(play(1, ageDaysAgo = 40), play(2, ageDaysAgo = 400))
        assertNull(RecapBuilder.build(history, listOf(song(1), song(2)), nowMs))
    }

    @Test
    fun topSongsFollowPlayCountDescending() {
        val library = listOf(song(1), song(2), song(3))
        val history = listOf(
            play(2, 1), play(2, 2), play(2, 3),
            play(3, 1), play(3, 5),
            play(1, 2)
        )
        val recap = RecapBuilder.build(history, library, nowMs)
        assertNotNull(recap)
        assertEquals(listOf(2L, 3L, 1L), recap!!.topSongs.map { it.id })
        assertEquals(6, recap.totalPlays)
        assertEquals((6 * 3.5).toLong(), recap.totalMinutes)
        // All three library songs share "Artist A": their play counts aggregate.
        assertEquals(6, recap.topArtists.first().second)
    }

    @Test
    fun blankArtistCollapsesIntoUnknownArtist() {
        val library = listOf(song(1, artist = ""), song(2))
        val history = listOf(play(1, 1), play(2, 1))
        val recap = RecapBuilder.build(history, library, nowMs)
        assertNotNull(recap)
        assertEquals("Unknown Artist", recap!!.topArtists.first().first)
        assertEquals(1, recap.topArtists.first().second)
    }
}
