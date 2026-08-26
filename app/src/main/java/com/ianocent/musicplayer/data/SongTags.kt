package com.ianocent.musicplayer.data

/**
 * Pure helpers that turn raw stream-provider metadata into clean ID3-style
 * values for downloaded files. No Android dependencies, so the whole module
 * is unit-testable; [MetadataWriter] and [SongDownloader] are the adapters
 * that apply these results to files.
 */
object SongTags {

    /** Audio container families this app knows how to tag or scan. */
    enum class Container { MP3, MP4, WEBM, UNKNOWN }

    /**
     * Sniffs the container from magic bytes. YouTube audio arrives as AAC in
     * an MP4 shell ("ftyp"), Opus/WebM ("EBML"), or rarely true MPEG audio;
     * the download filename may lie, the header does not.
     */
    fun detectContainer(header: ByteArray): Container {
        if (header.size >= 4 && header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) {
            return Container.MP3 // "ID3"
        }
        if (header.size >= 2 && (header[0].toInt() and 0xFF) == 0xFF && (header[1].toInt() and 0xE0) == 0xE0) {
            return Container.MP3
        }
        if (header.size >= 8 && header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
            header[6] == 0x79.toByte() && header[7] == 0x70.toByte()
        ) {
            return Container.MP4 // "ftyp" at offset 4
        }
        if (header.size >= 4 && header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return Container.WEBM // EBML
        }
        return Container.UNKNOWN
    }

    /**
     * Picks a file extension from a resolved stream URL by reading its
     * `mime=` query parameter (googlevideo URLs carry it percent-encoded).
     */
    fun extensionForUrl(url: String): String {
        val mime = Regex("mime=(audio%2F|audio/)([a-z0-9]+)", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.get(2)?.lowercase()
        return when (mime) {
            "mp4" -> "m4a"
            "webm" -> "webm"
            "mpeg", "mp3" -> "mp3"
            else -> "mp3"
        }
    }

    /** MIME type matching [extensionForUrl]; also derived from a filename. */
    fun mimeForExtension(extension: String?): String = when (extension?.lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "webm", "opus" -> "audio/webm"
        else -> "audio/mpeg"
    }

    private val TITLE_JUNK = listOf(
        Regex("""\s*[\(\[](official\s+)?(music\s+)?video[\)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\(\[](official\s+)?(lyric[s]?\s+)?video[\)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\(\[]official\s+(music\s+)?(audio|visualizer|mv)[\)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s*[\(\[](lyrics?|audio|hd|hq|4k|m\/v)[\)\]]""", RegexOption.IGNORE_CASE),
        Regex("""\s*-\s*(official\s+)?(music\s+)?(video|audio|lyric[s]?)(\s+video)?$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\|\s*(official\s+)?(music\s+)?video$""", RegexOption.IGNORE_CASE)
    )

    /**
     * Strips promotional suffixes ("(Official Video)", "[Lyrics]", ...) that
     * regular YouTube video titles carry but ID3 titles should not.
     */
    fun cleanTitle(rawTitle: String): String {
        var t = rawTitle.trim()
        var before = ""
        while (t != before) {
            before = t
            for (rx in TITLE_JUNK) t = t.replace(rx, "")
        }
        return t.trim().trimEnd('-', '|', '(', '[').trim().ifBlank { rawTitle.trim() }
    }

    private fun isJunkArtist(artist: String): Boolean =
        artist.isBlank() ||
            artist.startsWith("Unknown", ignoreCase = true) ||
            artist.equals("YouTube", ignoreCase = true) ||
            artist.contains("Various", ignoreCase = true)

    /**
     * Splits a combined "Artist - Title" video title into its parts when the
     * stored artist is untrustworthy (a channel name like "Ed Sheeran - Topic"
     * or "Unknown Artist"), and cleans both fields up.
     *
     * Returns (title, artist).
     */
    fun resolve(title: String, artist: String): Pair<String, String> {
        var t = cleanTitle(title)
        var a = artist.trim()

        // Channel-name cleanup: "Ed Sheeran - Topic" / "EdSheeranVEVO"
        if (a.endsWith(" - Topic", ignoreCase = true)) a = a.removeSuffix(" - Topic").trim()
        if (a.endsWith("VEVO", ignoreCase = true)) a = a.removeSuffix("VEVO").trim()

        val dash = t.indexOf(" - ")
        if (dash > 0) {
            val leftCandidate = t.substring(0, dash).trim()
            val rightCandidate = t.substring(dash + 3).trim()
            val artistIsJunk = isJunkArtist(a) || a.endsWith(" - Topic", true)
            val leftLooksLikeArtist = leftCandidate.length <= 60 && !leftCandidate.contains('(')
            val titleStartsWithArtist = a.isNotBlank() &&
                t.startsWith(a, ignoreCase = true) && t.getOrNull(a.length + 1) == '-'
            if ((artistIsJunk || titleStartsWithArtist) && rightCandidate.isNotBlank() && leftLooksLikeArtist) {
                return cleanTitle(rightCandidate) to leftCandidate
            }
        }

        if (a.isBlank()) a = artist.trim().ifBlank { "Unknown Artist" }
        return t to a
    }
}
