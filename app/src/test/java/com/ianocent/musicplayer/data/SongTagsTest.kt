package com.ianocent.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SongTags is pure — the interface IS the test surface. These cases pin the
 * normalisation contract that downloaded files' tags and MediaStore rows
 * depend on.
 */
class SongTagsTest {

    @Test
    fun `splits artist-title video title when artist is a Topic channel`() {
        val (title, artist) = SongTags.resolve(
            title = "Shape of You (Official Video)",
            artist = "Ed Sheeran - Topic"
        )
        assertEquals("Ed Sheeran", artist)
        assertEquals("Shape of You", title)
    }

    @Test
    fun `splits combined title when artist unknown`() {
        val (title, artist) = SongTags.resolve(
            title = "Tulus - Monokrom",
            artist = "Unknown Artist"
        )
        assertEquals("Tulus", artist)
        assertEquals("Monokrom", title)
    }

    @Test
    fun `keeps trusted artist and only cleans title`() {
        val (title, artist) = SongTags.resolve(
            title = "Monokrom",
            artist = "Tulus"
        )
        assertEquals("Tulus", artist)
        assertEquals("Monokrom", title)
    }

    @Test
    fun `strips promotional suffixes`() {
        assertEquals("Monokrom", SongTags.cleanTitle("Monokrom [Official Music Video]"))
        assertEquals("Monokrom", SongTags.cleanTitle("Monokrom (Lyrics)"))
        assertEquals("Monokrom", SongTags.cleanTitle("Monokrom - Official Music Video"))
        // No junk present -> unchanged
        assertEquals("Baby (Remix)", SongTags.cleanTitle("Baby (Remix)"))
    }

    @Test
    fun `blank title falls back to raw input`() {
        // cleanTitle never returns blank when raw had content
        assertEquals("( )", SongTags.cleanTitle("( )"))
    }

    @Test
    fun `detects containers from magic bytes`() {
        val ftyp = byteArrayOf(0, 0, 0, 0x20, 0x66, 0x74, 0x79, 0x70, 0x4D, 0x34, 0x41) // ....ftypM4A        assertEquals(SongTags.Container.MP4, SongTags.detectContainer(ftyp))

        val id3 = "ID3\u0004".toByteArray(Charsets.ISO_8859_1)
        assertEquals(SongTags.Container.MP3, SongTags.detectContainer(id3))

        val ebml = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
        assertEquals(SongTags.Container.WEBM, SongTags.detectContainer(ebml))

        val mpegFrame = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00)
        assertEquals(SongTags.Container.MP3, SongTags.detectContainer(mpegFrame))
    }

    @Test
    fun `extension follows url mime parameter`() {
        assertEquals("m4a", SongTags.extensionForUrl("https://x/videoplayback?mime=audio%2Fmp4&itag=140"))
        assertEquals("webm", SongTags.extensionForUrl("https://x/videoplayback?mime=audio%2Fwebm"))
        assertEquals("mp3", SongTags.extensionForUrl("https://example.com/song.mp3"))
        assertEquals("mp3", SongTags.extensionForUrl("https://x/stream?id=1"))
    }

    @Test
    fun `mime matches extension`() {
        assertEquals("audio/mp4", SongTags.mimeForExtension("m4a"))
        assertEquals("audio/webm", SongTags.mimeForExtension("webm"))
        assertEquals("audio/mpeg", SongTags.mimeForExtension("mp3"))
        assertEquals("audio/mpeg", SongTags.mimeForExtension(null))
    }
}
