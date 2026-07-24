package com.ianocent.musicplayer.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber
import android.view.Surface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ianocent.musicplayer.data.LyricLine
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.player.WaveProjectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sqrt

// ---------- ANIMATED LYRIC ----------


// ---------- ANIMATED LYRIC ----------
// FIX BUG B: sebelumnya prevText gapernah di-reassign ke text baru, jadi abis line
// pertama, state freeze permanen dan lyric keliatan blank/stuck. Sekarang semua logic
// (showText toggle + prevText reassignment) disatuin di dalam satu LaunchedEffect(text).

@Composable
fun AnimatedLyricLine(syncedLyric: List<LyricLine>?, plainLyric: String?, currentPosition: Long, accentColor: Color, modifier: Modifier = Modifier) {
    val text = if (!syncedLyric.isNullOrEmpty()) {
        val idx = syncedLyric.indexOfLast { it.timeMs <= currentPosition }
        if (idx >= 0) syncedLyric[idx].text else ""
    } else if (!plainLyric.isNullOrBlank()) {
        val lines = plainLyric.lines().filter { it.isNotBlank() }
        lines.getOrElse(((currentPosition % 5000L).toFloat() / 5000f * lines.size).toInt().coerceAtMost(lines.size - 1)) { "" }
    } else ""
    var prevText by remember { mutableStateOf(text) }
    var showText by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        if (text != prevText) {
            showText = false
            delay(50)
            prevText = text
            showText = true
        }
    }
    val alpha = animateFloatAsState(if (showText) 1f else 0f, animationSpec = tween(120, delayMillis = 50, easing = LinearEasing))
    val scale = animateFloatAsState(if (showText) 1f else 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
    val translateY = animateFloatAsState(if (showText) 0f else 20f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = prevText.ifEmpty { "\u266A" },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alpha.value
                    this.scaleX = scale.value
                    this.scaleY = scale.value
                    this.translationY = translateY.value
                }
        )
    }
}

// ---------- CARD ----------

@Composable
fun WaveRecordContent(song: Song?, syncedLyric: List<LyricLine>?, plainLyric: String?, currentPosition: Long, albumArt: android.graphics.Bitmap?, accentColor: Color, modifier: Modifier = Modifier) {
    val hl = !syncedLyric.isNullOrEmpty() || !plainLyric.isNullOrBlank()
    Box(modifier.width(360.dp).height(250.dp).clip(RoundedCornerShape(20.dp))) {
        if (albumArt != null) Image(albumArt.asImageBitmap(), null, Modifier.fillMaxSize().blur(30.dp), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(accentColor))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(0.85f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.Bottom) {
            if (hl) {
//                Text(
//                    "\u201C",
//                    fontSize = 56.sp,
//                    color = Color.White.copy(alpha = 0.4f),
//                    fontWeight = FontWeight.Black,
//                    textAlign = TextAlign.Start
//                )
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    AnimatedLyricLine(syncedLyric, plainLyric, currentPosition, accentColor, Modifier.fillMaxWidth()) 
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            
            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 24.dp).background(Color.White, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = song?.title ?: "", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    Text(text = song?.artist ?: "", color = Color.White.copy(0.7f), fontSize = 12.sp, maxLines = 1)
                }
                Text(text = "ıanocent", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------- AV RECORDER (video frames + system audio) ----------

private class AvRecorder(
    private val width: Int,
    private val height: Int,
    private val bitRate: Int = 10_000_000,
    private val frameRate: Int = 60,
    private val sampleRate: Int = 44100,
    private val mediaProjection: MediaProjection? = null,
    private val useSystemAudio: Boolean = false
) {
    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var audioRecord: AudioRecord? = null
    private var videoTrack = -1
    private var audioTrack = -1
    var isMuxerStarted = false
    private var videoFormatReady = false
    private var audioFormatReady = false
    private var ptsUs = 0L
    private var audioPtsUs = 0L
    private var startNs = -1L
    private val drainLock = Any()
    @Volatile var isRunning = false
    private var audioFeedJob: kotlinx.coroutines.Job? = null
    private val audioFeedScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

    fun setStartTime(ns: Long) {
        startNs = ns
    }

    fun startVideo(): Boolean = try {
        videoCodec = MediaCodec.createEncoderByType("video/avc").apply {
            configure(MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }
        true
    } catch (e: Exception) { Timber.e(e, "video codec start failed"); false }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startAudio(): Boolean = try {
        audioCodec = MediaCodec.createEncoderByType("audio/mp4a-latm").apply {
            configure(MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 2).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        if (useSystemAudio && mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val audioPlaybackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val af = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
            val bs = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 4
            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(audioPlaybackConfig)
                .setAudioFormat(af).setBufferSizeInBytes(bs).build().apply { startRecording() }
        } else {
            val af = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
            val bs = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(af).setBufferSizeInBytes(bs).build().apply { startRecording() }
        }
        true
    } catch (e: Exception) { Timber.e(e, "audio start failed"); false }

    fun startAudioFeed() {
        audioFeedJob = audioFeedScope.launch {
            while (isRunning) {
                if (audioRecord == null) {
                    delay(50)
                    continue
                }
                feedAudioOnce()
            }
        }
    }

    private fun feedAudioOnce() {
        if (audioRecord == null || audioCodec == null || !isRunning) return
        try {
            val buf = ByteArray(4096)
            val read = audioRecord!!.read(buf, 0, buf.size)
            if (read <= 0) return
            val idx = audioCodec!!.dequeueInputBuffer(0L)
            if (idx < 0) return
            val bb = audioCodec!!.getInputBuffer(idx) ?: return
            bb.clear(); bb.put(buf, 0, read)
            
            val currentPtsUs = if (startNs > 0) (System.nanoTime() - startNs) / 1000 else audioPtsUs
            audioCodec!!.queueInputBuffer(idx, 0, read, currentPtsUs, 0)
            
            // Still increment ideal PTS to maintain continuity if system clock jitters
            audioPtsUs += (read.toLong() * 1_000_000L / (sampleRate * 2L * 2L))
            drainAudio(false)
        } catch (_: Exception) {}
    }

    fun startMuxer(path: String) {
        muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        isMuxerStarted = false
    }

    fun frame(bitmap: ImageBitmap, presentationTimeUs: Long) {
        try {
            val s = inputSurface ?: return
            val c = s.lockHardwareCanvas()
            val paint = android.graphics.Paint().apply { isFilterBitmap = true }
            val srcW = bitmap.width
            val srcH = bitmap.height
            val scale = minOf(width.toFloat() / srcW, height.toFloat() / srcH)
            val dstW = (srcW * scale).toInt()
            val dstH = (srcH * scale).toInt()
            val left = (width - dstW) / 2
            val top = (height - dstH) / 2
            c.drawBitmap(bitmap.asAndroidBitmap(), null, android.graphics.Rect(left, top, left + dstW, top + dstH), paint)
            s.unlockCanvasAndPost(c)
            ptsUs = presentationTimeUs
            drainVideo(false)
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        audioFeedJob?.cancel()
        try { videoCodec?.signalEndOfInputStream() } catch (_: Exception) {}
        try { audioCodec?.signalEndOfInputStream() } catch (_: Exception) {}
        try { audioRecord?.stop() } catch (_: Exception) {}
        drainVideo(true)
        drainAudio(true)
        try { videoCodec?.stop() } catch (_: Exception) {}
        try { videoCodec?.release() } catch (_: Exception) {}
        try { audioCodec?.stop() } catch (_: Exception) {}
        try { audioCodec?.release() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        if (isMuxerStarted) { try { muxer?.stop() } catch (_: Exception) {} }
        try { muxer?.release() } catch (_: Exception) {}
        videoCodec = null; audioCodec = null; inputSurface = null; audioRecord = null; muxer = null
        videoTrack = -1; audioTrack = -1; isMuxerStarted = false
    }

    private fun drainVideo(eos: Boolean) {
        val c = videoCodec ?: return
        val m = muxer ?: return
        var consecutiveEmpty = 0
        while (true) {
            val info = MediaCodec.BufferInfo()
            val idx = c.dequeueOutputBuffer(info, 10_000L)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!eos) return
                    consecutiveEmpty++
                    if (consecutiveEmpty > 10) return
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(drainLock) { if (!isMuxerStarted && !videoFormatReady) { videoTrack = m.addTrack(c.outputFormat); videoFormatReady = true; if (audioFormatReady || audioCodec == null) { m.start(); isMuxerStarted = true } } }
                idx >= 0 -> {
                    consecutiveEmpty = 0
                    val buf = c.getOutputBuffer(idx) ?: return
                    if (videoTrack >= 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) { info.presentationTimeUs = ptsUs; synchronized(drainLock) { m.writeSampleData(videoTrack, buf, info) } }
                    c.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun drainAudio(eos: Boolean) {
        val c = audioCodec ?: return
        val m = muxer ?: return
        var consecutiveEmpty = 0
        while (true) {
            val info = MediaCodec.BufferInfo()
            val idx = c.dequeueOutputBuffer(info, 10_000L)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!eos) return
                    consecutiveEmpty++
                    if (consecutiveEmpty > 10) return
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(drainLock) { if (!isMuxerStarted && !audioFormatReady) { audioTrack = m.addTrack(c.outputFormat); audioFormatReady = true; if (videoFormatReady) { m.start(); isMuxerStarted = true } } }
                idx >= 0 -> {
                    consecutiveEmpty = 0
                    if (audioTrack < 0) { c.releaseOutputBuffer(idx, false); continue }
                    val buf = c.getOutputBuffer(idx) ?: return
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) synchronized(drainLock) { m.writeSampleData(audioTrack, buf, info) }
                    c.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }
}

// ---------- SHEET ----------
// FIX BUG A: sebelumnya seluruh siklus record (setup -> loop -> stop -> save ke MediaStore)
// dibungkus LaunchedEffect(isRecording). Begitu tombol Stop mengubah isRecording ke false,
// Compose langsung me-restart/cancel LaunchedEffect itu SEBELUM baris rec.stop() dan proses
// save-to-gallery sempat jalan -> makanya file gapernah kesimpen (coroutine kepotong duluan).
// Sekarang proses record dijalanin lewat coroutineScope.launch biasa (startRecording()),
// yang independen dari recomposition/key change, jadi rec.stop() + save pasti kelar duluan
// sebelum job selesai.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveRecordSheet(
    song: Song?,
    syncedLyric: List<LyricLine>?,
    plainLyric: String?,
    currentPositionValue: Long, // Reactive value from parent
    currentPosition: () -> Long,
    albumArt: android.graphics.Bitmap?,
    accentColor: Color,
    isPlaying: Boolean,
    audioSessionId: Int,
    onDismiss: () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    var isRecording by remember { mutableStateOf(false) }
    var recorderRef = remember { mutableStateOf<AvRecorder?>(null) }
    var mediaProjection by remember { mutableStateOf<MediaProjection?>(null) }

    // Start WaveProjectionService immediately so it's active when getMediaProjection() fires
    LaunchedEffect(Unit) {
        ContextCompat.startForegroundService(context, Intent(context, WaveProjectionService::class.java))
    }

    // Observed position for recomposition during recording
    var observedPosition by remember { mutableStateOf(0L) }
    
    // Use currentPositionValue as base when not recording
    val displayPosition = if (isRecording) observedPosition else currentPositionValue

    val projLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            try {
                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mgr.getMediaProjection(result.resultCode, result.data!!)
            } catch (e: Exception) {
                Timber.e(e, "MediaProjection init failed")
            }
        }
    }
    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WaveProjectionService::class.java))
                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projLauncher.launch(mgr.createScreenCaptureIntent())
            } catch (e: Exception) {
                Timber.e(e, "Screen capture intent launch failed")
            }
        } else {
            Timber.w("RECORD_AUDIO denied, recording without system audio")
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        coroutineScope.launch {
            try {

            val tempPath = context.cacheDir.resolve("wave_temp.mp4").absolutePath
            val cardW = with(density) { 360.dp.toPx().toInt().coerceAtMost(720).let { it - (it % 16) } }
            val cardH = with(density) { 250.dp.toPx().toInt().coerceAtMost(500).let { it - (it % 16) } }
            val bps = (4_000_000L * cardW * cardH / (360 * 480)).toInt().coerceIn(3_000_000, 10_000_000)
            val rec = AvRecorder(cardW, cardH, bitRate = bps, frameRate = 60, mediaProjection = mediaProjection, useSystemAudio = true)

            if (!rec.startVideo()) { isRecording = false; return@launch }
            if (!rec.startAudio()) { Timber.w("audio start failed") }

            val startNs = System.nanoTime()
            rec.setStartTime(startNs)
            rec.isRunning = true
            rec.startAudioFeed()
            rec.startMuxer(tempPath)
            recorderRef.value = rec
            isRecording = true

            try {
                // Skip first frame (graphicsLayer might be stale)
                withFrameNanos { }
                withFrameNanos { }
            } catch (_: Exception) {}

            var lastNs = 0L
            val intervalNs = 1_000_000_000L / 60 // 60 FPS
            while (isActive && isRecording) {
                try {
                    val frameNs = withFrameNanos { it }
                    if (frameNs - lastNs >= intervalNs) {
                        lastNs = frameNs
                        val ptsUs = (frameNs - startNs) / 1000
                        // Update observed position to trigger recomposition with latest lyrics
                        observedPosition = currentPosition()
                        rec.frame(graphicsLayer.toImageBitmap(), ptsUs)
                    }
                } catch (_: Exception) { break }
            }

            // Bagian ini sekarang SELALU jalan sampai selesai, gak lagi ke-cancel
            // duluan oleh recomposition/key change dari isRecording.
            rec.stop()
            context.stopService(Intent(context, WaveProjectionService::class.java))
            val muxerWasStarted = rec.isMuxerStarted
            recorderRef.value = null

            // Save to gallery
            withContext(Dispatchers.IO) {
                try {
                    val f = File(tempPath)
                    Timber.d("save: file exists=${f.exists()} size=${f.length()} muxerStarted=$muxerWasStarted")
                    if (!f.exists() || f.length() == 0L) {
                        Timber.e("save: empty file, skipping")
                        return@withContext
                    }
                    val filename = "wave_${System.currentTimeMillis()}.mp4"
                    val cv = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Pictures/IanPlayer")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
                    Timber.d("save: uri=$uri")
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { out ->
                            f.inputStream().use { inp -> inp.copyTo(out) }
                        }
                        Timber.d("save: copied to media store")
                    }
                    f.delete()
                    Timber.d("save: done")
                } catch (e: Exception) { Timber.e(e, "save failed") }
            }
            } catch (e: Exception) {
                Timber.e(e, "record failed")
                isRecording = false
                recorderRef.value = null
            }
        }
    }

    // MediaProjection didapat async lewat callback projLauncher. Begitu mediaProjection
    // ke-set dan belum ada recording jalan, langsung trigger startRecording().
    LaunchedEffect(mediaProjection) {
        if (mediaProjection != null && recorderRef.value == null && !isRecording) {
            startRecording()
        }
    }

    ModalBottomSheet(onDismissRequest = {
        isRecording = false
        context.stopService(Intent(context, WaveProjectionService::class.java))
        onDismiss()
    }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            }) {
                WaveRecordContent(song, syncedLyric, plainLyric, displayPosition, albumArt, accentColor)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isRecording) {
                    Button(
                        onClick = {
                            val hasAudioPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                            when {
                                mediaProjection != null && hasAudioPerm -> startRecording()
                                mediaProjection != null -> recordAudioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                hasAudioPerm -> {
                                    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    projLauncher.launch(mgr.createScreenCaptureIntent())
                                }
                                else -> recordAudioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Icon(Icons.Filled.FiberManualRecord, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Record")
                    }
                } else {
                    Button(
                        onClick = {
                            // Cukup matiin flag isRecording -> while loop di startRecording()
                            // keluar natural, lalu job yang sama yang urus rec.stop() + save.
                            // Ini hindari double-stop race dari 2 caller berbeda.
                            isRecording = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Stop")
                    }
                }
                if (!isRecording) {
                    OutlinedButton(onClick = {
                        coroutineScope.launch {
                            try { saveBitmapToGallery(context, graphicsLayer.toImageBitmap().asAndroidBitmap()) }
                            catch (e: Exception) { Timber.w(e, "img fail") }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Save to Gallery") }
                }
            }
        }
    }
}