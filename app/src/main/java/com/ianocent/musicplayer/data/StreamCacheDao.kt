package com.ianocent.musicplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StreamCacheDao {
    @Query("SELECT * FROM cached_stream_urls WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): CachedStreamUrl?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CachedStreamUrl)

    // Housekeeping ringan biar tabel gak numpuk data basi selamanya
    @Query("DELETE FROM cached_stream_urls WHERE expiresAtMs < :nowMs")
    suspend fun deleteExpired(nowMs: Long)
}