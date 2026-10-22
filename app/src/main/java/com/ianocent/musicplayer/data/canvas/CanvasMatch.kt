package com.ianocent.musicplayer.data.canvas

data class CanvasMatch(
    val ok: Boolean = true,
    val matched: Boolean = false,
    val confidence: Float = 0f,
    val spotifyId: String? = null,
    val canvasUrl: String? = null,
    val canvasType: String? = null,
    val artistName: String? = null,
    val urlExpiresAt: Long? = null,
    val source: String? = null,
    val error: String? = null
)
