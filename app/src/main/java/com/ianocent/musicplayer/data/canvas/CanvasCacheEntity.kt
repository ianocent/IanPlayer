package com.ianocent.musicplayer.data.canvas

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canvas_cache")
data class CanvasCacheEntity(
    @PrimaryKey val songKey: String,
    val matched: Boolean,
    val confidence: Float,
    val spotifyId: String?,
    val canvasUrl: String?,
    val canvasType: String?,
    val artistName: String?,
    val urlExpiresAt: Long?,
    val source: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
