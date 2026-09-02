package com.ianocent.musicplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Identifies songs by recording ambient audio. Currently stubbed out as the
 * Google MusicRecognitionManager is not available as a public API.
 *
 * The audio recording infrastructure is kept for future integration with
 * a real recognition API (e.g., Shazam/AudD).
 */
class MusicRecognitionManager(private val context: Context) {

    data class Result(
        val title: String,
        val artist: String,
        val album: String?,
        val artworkUrl: String?
    )

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(
        durationMs: Int = 10_000,
        onResult: (Result?) -> Unit,
        onRmsChanged: ((Float) -> Unit)? = null
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onResult(null)
            return
        }

        // Recognition not available — return null immediately
        // TODO: Integrate a real recognition API (Shazam/AudD)
        onResult(null)
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release()
        audioRecord = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
