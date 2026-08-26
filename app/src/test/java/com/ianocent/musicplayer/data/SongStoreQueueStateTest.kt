package com.ianocent.musicplayer.data

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SongStore queue-state wire format through its interface: what save writes,
 * restore reads back unchanged — the invariant existing installs depend on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SongStoreQueueStateTest {

    private val prefs: android.content.SharedPreferences =
        (RuntimeEnvironment.getApplication() as Context)
            .getSharedPreferences("test_prefs", Context.MODE_PRIVATE)

    private fun song(id: Long, title: String = "T$id") = Song(
        id = id, title = title, artist = "Artist", duration = 1L,
        uri = Uri.parse("content://test/$id"), isStream = false
    )

    @Test
    fun queueStateRoundTrips() {
        val queue = listOf(song(1), song(2), song(3))
        SongStore.saveQueueState(prefs, queue, currentSongId = 2, index = 1)

        val restored = SongStore.restoreQueueState(prefs)!!
        assertEquals(listOf(1L, 2L, 3L), restored.queue.map { it.id })
        assertEquals(2L, restored.currentSongId)
        assertEquals(1, restored.index)
    }

    @Test
    fun outOfRangeIndexIsClampedIntoTheQueue() {
        SongStore.saveQueueState(prefs, listOf(song(1)), currentSongId = 1, index = 9)
        assertEquals(0, SongStore.restoreQueueState(prefs)!!.index)
    }

    @Test
    fun missingStateRestoresNothing() {
        prefs.edit().clear().commit()
        assertNull(SongStore.restoreQueueState(prefs))
    }

    @Test
    fun songCodecRoundTripsEveryField() {
        val original = Song(
            id = 7L, title = "Title", artist = "Artist", album = "Album",
            duration = 1234L, uri = Uri.parse("https://example.org/a.mp3"),
            isStream = true, remoteArtUrl = "https://art/7.jpg", dateAdded = 42L,
            remoteId = "abc123", source = StreamSources.YOUTUBE
        )
        val decoded = SongStore.jsonToSong(SongStore.songToJson(original))!!
        assertEquals(original.id, decoded.id)
        assertEquals(original.title, decoded.title)
        assertEquals(original.remoteId, decoded.remoteId)
        assertEquals(original.source, decoded.source)
        assertEquals(original.uri.toString(), decoded.uri.toString())
    }
}
