package com.ianocent.musicplayer.data.canvas

import android.util.Log

/**
 * Single-log-tag helper for the whole Spotify canvas pipeline.
 * Filter logcat with:  tag:Canvas
 */
internal object CanvasLog {
    private const val TAG = "Canvas"

    fun d(msg: String) {
        Log.d(TAG, msg)
    }

    fun e(msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(TAG, msg, tr) else Log.e(TAG, msg)
    }
}
