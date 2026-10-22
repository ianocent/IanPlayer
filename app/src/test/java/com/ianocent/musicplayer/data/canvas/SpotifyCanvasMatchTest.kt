package com.ianocent.musicplayer.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ianocent.musicplayer.data.canvas.SpotifyCanvasApi.SpotifyTrackCandidate

/**
 * Pure-JVM checks for the Spotify canvas matching pipeline: text hygiene,
 * similarity, candidate acceptance rules and TOTP determinism.
 */
class SpotifyCanvasMatchTest {

    // ---------- normalize ----------

    @Test
    fun `normalize strips parenthesized noise and punctuation`() {
        assertEquals(
            "kill bill",
            SpotifyCanvasRepository.normalize("Kill Bill (Official Video) [HQ]")
        )
        assertEquals(
            "flashing lights",
            SpotifyCanvasRepository.normalize("Flashing Lights (feat. someone else) - Official Music Video")
        )
        assertEquals(
            "dont dream its over",
            SpotifyCanvasRepository.normalize("Don't Dream It's Over...")
        )
    }

    @Test
    fun `normalize collapses whitespace`() {
        assertEquals("one two", SpotifyCanvasRepository.normalize("  One    Two  "))
    }

    // ---------- similarity ----------

    @Test
    fun `similarity is exact for identical strings`() {
        assertEquals(1f, SpotifyCanvasRepository.similarity("kill bill", "kill bill"))
    }

    @Test
    fun `similarity handles empty operands`() {
        assertEquals(1f, SpotifyCanvasRepository.similarity("", ""))
        assertEquals(0f, SpotifyCanvasRepository.similarity("a", ""))
        assertEquals(0f, SpotifyCanvasRepository.similarity("", "a"))
    }

    // ---------- bestCandidate ----------

    private fun cand(
        id: String,
        title: String,
        artist: String,
        durationMs: Long = 0L,
    ) = SpotifyTrackCandidate(id, title, artist, durationMs)

    @Test
    fun `picks the duration-matched candidate with high confidence`() {
        val best = SpotifyCanvasRepository.bestCandidate(
            songTitle = "Kill Bill",
            songArtist = "SZA",
            songDurationMs = 153_946L,
            candidates = listOf(
                cand("spedup", "Kill Bill - Sped Up Version", "SZA", 137_528L),
                cand("main", "Kill Bill", "SZA", 153_946L),
            )
        ) ?: throw AssertionError("expected a candidate")
        assertEquals("main", best.candidate.spotifyId)
        assertTrue("confidence ${best.confidence}", best.confidence >= 0.60f)
    }

    @Test
    fun `rejects remix whose duration differs wildly`() {
        val best = SpotifyCanvasRepository.bestCandidate(
            songTitle = "Kill Bill",
            songArtist = "SZA",
            songDurationMs = 153_946L,
            candidates = listOf(
                cand("live", "Kill Bill (Lo-fi Remix)", "SZA", 300_000L),
            )
        )
        // 146s duration gap → durationSim 0 → confidence below threshold
        assertTrue(best == null || best.confidence < 0.60f)
    }

    @Test
    fun `unknown duration still allows a strong title-artist match`() {
        val best = SpotifyCanvasRepository.bestCandidate(
            songTitle = "Blinding Lights",
            songArtist = "The Weeknd",
            songDurationMs = 0L,
            candidates = listOf(
                cand("x", "Blinding Lights", "The Weeknd"),
            )
        )
        assertTrue(best != null && best.confidence >= 0.60f)
    }

    @Test
    fun `unknown artist does not veto otherwise good match`() {
        val best = SpotifyCanvasRepository.bestCandidate(
            songTitle = "Kill Bill",
            songArtist = "",
            songDurationMs = 153_946L,
            candidates = listOf(
                cand("x", "Kill Bill", "SZA", 153_946L),
            )
        )
        assertTrue(best != null)
    }

    @Test
    fun `different artist is rejected`() {
        val best = SpotifyCanvasRepository.bestCandidate(
            songTitle = "Kill Bill",
            songArtist = "TOTAL DUKUN",
            songDurationMs = 153_946L,
            candidates = listOf(
                cand("x", "Kill Bill", "SZA", 153_946L),
            )
        )
        assertTrue(best == null)
    }

    @Test
    fun `ordinary stream title noise is cleaned before matching`() {
        val best = SpotifyCanvasRepository.bestCandidate(
            songTitle = "Flashing Lights (feat. Dwele) - Official Music Video",
            songArtist = "Kanye West",
            songDurationMs = 240_000L,
            candidates = listOf(
                cand("x", "Flashing Lights (feat. Dwele)", "Kanye West", 240_000L),
            )
        )
        assertTrue(best != null && best.confidence >= 0.60f)
    }

    // ---------- TOTP ----------

    @Test
    fun `totp output is 6 digits and stable within the period window`() {
        val secrets = listOf(99, 111, 47, 88, 49, 56, 118, 65, 52, 67) // arbitrary
        val t = 1_772_000_000_000L
        val code = SpotifyTokenManager.computeTotp(t, secrets)
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
        // same counter window → same code
        assertEquals(code, SpotifyTokenManager.computeTotp(t + 20_000, secrets))
        // different window → (almost certainly) different code
        assertFalse("codes equal across windows", code == SpotifyTokenManager.computeTotp(t + 31_000, secrets))
    }
}
