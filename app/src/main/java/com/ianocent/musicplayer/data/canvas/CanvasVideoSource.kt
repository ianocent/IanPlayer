package com.ianocent.musicplayer.data.canvas

import android.content.Context
import android.os.Environment
import com.ianocent.musicplayer.data.Song
import java.io.File

/**
 * Resolves a user-provided canvas video for a song. Looks for a file next to
 * the local audio file (same base name, video extensions) or inside a dedicated
 * "IanCanvas" folder under Music. Stream songs match by remoteId/title/artist.
 */
object CanvasVideoSource {
    private val EXTS = listOf("mp4", "webm", "mov", "mkv")

    fun resolve(song: Song?, context: Context): String? {
        if (song == null) return null
        val candidates = mutableListOf<String>()

        if (!song.isStream) {
            val p = song.uri.path
            if (p != null && File(p).isFile) {
                val file = File(p)
                val dir = file.parentFile
                if (dir != null) {
                    val base = file.nameWithoutExtension
                    for (ext in EXTS) {
                        candidates += File(dir, "$base.canvas.$ext").absolutePath
                        candidates += File(dir, "$base.$ext").absolutePath
                    }
                }
            }
        }

        val canvasDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "IanCanvas"
        )
        val names = buildList {
            add(song.remoteId)
            add(song.title)
            if (song.artist.isNotBlank()) {
                add("${song.artist} - ${song.title}")
                add(song.artist)
            }
        }
        for (n in names) {
            if (n.isNullOrBlank()) continue
            for (ext in EXTS) {
                candidates += File(canvasDir, "$n.$ext").absolutePath
            }
        }

        return candidates.firstOrNull { File(it).exists() }
    }
}