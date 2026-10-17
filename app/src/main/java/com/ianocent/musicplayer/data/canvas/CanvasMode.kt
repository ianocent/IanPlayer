package com.ianocent.musicplayer.data.canvas

import com.ianocent.musicplayer.data.SettingsStore

enum class CanvasMode(val persisted: Int) {
    GENERATIVE(SettingsStore.CANVAS_GENERATIVE),
    VIDEO(SettingsStore.CANVAS_VIDEO),
    KEN_BURNS(SettingsStore.CANVAS_KEN_BURNS);

    companion object {
        fun from(persisted: Int): CanvasMode? =
            entries.firstOrNull { it.persisted == persisted }
    }
}