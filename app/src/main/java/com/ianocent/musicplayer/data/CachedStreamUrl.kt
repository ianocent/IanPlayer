package com.ianocent.musicplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_stream_urls")
data class CachedStreamUrl(
    @PrimaryKey val videoId: String,
    val url: String,
    val resolvedAtMs: Long,
    // Diparse dari param 'expire=' di URL googlevideo kalau ada (paling akurat).
    // Kalau gak ketemu (misal dari fallback Invidious), pakai fallback TTL 5 jam
    // dari resolvedAtMs — nilai ini dihitung SEKALI saat insert, bukan tiap query.
    val expiresAtMs: Long
)