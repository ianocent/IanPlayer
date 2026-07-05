package com.ianocent.musicplayer.data

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: MutableList<Long> = mutableListOf()
)