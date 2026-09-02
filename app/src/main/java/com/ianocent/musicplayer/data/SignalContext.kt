package com.ianocent.musicplayer.data

/**
 * Rich context captured from a social notification.
 */
data class SignalContext(
    val rawText: String,
    val artist: String?,
    val title: String?,
    val contextKeywords: List<String>,
    val sourceApp: String,
    val timeOfDay: String,
    val dayOfWeek: String,
    val timestamp: Long
)
