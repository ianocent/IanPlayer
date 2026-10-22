package com.ianocent.musicplayer.data.canvas

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CanvasCacheDao {
    @Query("SELECT * FROM canvas_cache WHERE songKey = :key")
    suspend fun get(key: String): CanvasCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CanvasCacheEntity)

    @Query("DELETE FROM canvas_cache WHERE songKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM canvas_cache")
    suspend fun clearAll()
}
