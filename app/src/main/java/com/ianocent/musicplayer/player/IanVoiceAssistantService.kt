package com.ianocent.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale

class IanVoiceAssistantService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private var isListening = false
    private var commandPending = false
    private var commandTimeoutJob: Job? = null
    private var isProcessing = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var errorCount = 0
    private var firstErrorTime = 0L

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val CHANNEL_ID = "voice_assistant_channel"
        private const val NOTIFICATION_ID = 1002
        private const val TAG = "VA"

        fun start(context: Context) {
            val intent = Intent(context, IanVoiceAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IanVoiceAssistantService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        log("Service create")
        setupNotification()
        setupMediaController()
        initTts()
        setupRecognizer()
    }

    private fun log(msg: String) { Timber.tag(TAG).d(msg) }
    private fun logWarn(msg: String) { Timber.tag(TAG).w(msg) }
    private fun logErr(msg: String) { Timber.tag(TAG).e(msg) }
    private fun logCmd(cmd: String, action: String, raw: String) { Timber.tag(TAG).i("CMD|$cmd|$action|$raw") }
    private fun logSpeech(type: String, text: String) { Timber.tag(TAG).i("SPEECH|$type|$text") }
    private fun logTts(text: String) { Timber.tag(TAG).i("TTS|$text") }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    engine.language = Locale.forLanguageTag("id-ID")
                    val maleVoice = engine.voices.find { v ->
                        v.name.contains("male", true) && v.locale.language == "id"
                    } ?: engine.voices.find { v ->
                        v.name.contains("male", true)
                    }
                    if (maleVoice != null) {
                        engine.voice = maleVoice
                        log("TTS male voice: ${maleVoice.name}")
                    } else {
                        engine.setPitch(0.6f)
                        engine.setSpeechRate(0.85f)
                        log("TTS no male voice found, using default with low pitch")
                    }
                    ttsReady = true
                }
            } else {
                logWarn("TTS init failed: $status")
            }
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            log("TTS not ready, skipped: $text")
            return
        }
        logTts(text)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun setupNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Voice Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ian Assistant Active")
            .setContentText("Listening for 'Halo Yan'")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            log("MediaController connected")
        }, MoreExecutors.directExecutor())
    }

    private fun setupRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                log("Ready for speech")
            }
            override fun onBeginningOfSpeech() {
                log("Beginning of speech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                log("End of speech")
            }
            override fun onError(error: Int) {
                isListening = false

                val errStr = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                    SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                    SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                    SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                    SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                    else -> "UNKNOWN($error)"
                }
                logWarn("Recognizer error: $errStr (#${++errorCount})")

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    logWarn("RECORD_AUDIO not granted, stopping assistant")
                    stopSelf()
                    return
                }

                val now = System.currentTimeMillis()
                if (firstErrorTime == 0L) firstErrorTime = now

                val backoffDelay = if (now - firstErrorTime < 10000 && errorCount > 3) {
                    5000L
                } else {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> 1200L
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 2000L
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> 4000L
                        else -> 1500L
                    }
                }

                if (now - firstErrorTime > 15000) {
                    errorCount = 0
                    firstErrorTime = 0L
                }

                serviceScope.launch {
                    delay(backoffDelay)
                    restartListening()
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase()?.trim()
                if (text != null) onFinalResult(text)
                scheduleRestart()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase()?.trim()
                if (text != null) onPartialResult(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
        }

        startListening()
    }

    private fun onPartialResult(text: String) {
        if (text.length < 3) return
        logSpeech("partial", text)
        if (isProcessing) return

        var cleaned = text
            .replace(Regex("[,.:!?\\s]+$"), "")
            .trim()

        if (commandPending) {
            if (cleaned.length >= 3) {
                log("Partial command: '$cleaned'")
                executeCommand(cleaned)
                commandPending = false
                commandTimeoutJob?.cancel()
            }
            return
        }

        val wakeHit = findWakeWord(cleaned)
        if (wakeHit != null) {
            isProcessing = true
            log("Wake word detected: '$wakeHit' in '$cleaned'")
            val after = cleaned.substringAfter(wakeHit).trim()
                .replace(Regex("^[,.:!?\\s]+"), "")
                .trim()
            if (after.length >= 3) {
                log("Inline command: '$after'")
                serviceScope.launch {
                    speak("Ya, ada apa?")
                    delay(600)
                    executeCommand(after)
                    isProcessing = false
                }
            } else {
                commandPending = true
                serviceScope.launch {
                    speak("Ya, ada apa?")
                    delay(400)
                    isProcessing = false
                }
                commandTimeoutJob?.cancel()
                commandTimeoutJob = serviceScope.launch {
                    delay(8000)
                    if (commandPending) {
                        commandPending = false
                        speak("Ya?")
                    }
                }
            }
        }
    }

    private fun onFinalResult(text: String) {
        if (text.length < 3) return
        logSpeech("final", text)
        if (isProcessing) return

        isProcessing = true

        var cleaned = text
            .replace(Regex("[,.:!?\\s]+$"), "")
            .trim()

        if (commandPending) {
            commandPending = false
            commandTimeoutJob?.cancel()
            serviceScope.launch {
                executeCommand(cleaned)
                delay(400)
                isProcessing = false
            }
            return
        }

        val wakeHit = findWakeWord(cleaned)
        if (wakeHit != null) {
            val after = cleaned.substringAfter(wakeHit).trim()
                .replace(Regex("^[,.:!?\\s]+"), "")
                .trim()
            if (after.length >= 3) {
                serviceScope.launch {
                    executeCommand(after)
                    delay(400)
                    isProcessing = false
                }
            } else {
                commandPending = true
                serviceScope.launch {
                    speak("Ya, ada apa?")
                    delay(400)
                    isProcessing = false
                }
                commandTimeoutJob?.cancel()
                commandTimeoutJob = serviceScope.launch {
                    delay(8000)
                    if (commandPending) {
                        commandPending = false
                        speak("Ya?")
                    }
                }
            }
            return
        }

        val directClean = cleaned.lowercase().trim()
        val executed = tryDirectCommand(directClean)
        serviceScope.launch {
            delay(400)
            isProcessing = false
        }
        if (!executed) {
            logCmd("ignore", "no_wake_no_direct", cleaned)
        }
    }

    private fun findWakeWord(text: String): String? {
        val wakeWords = listOf("halo yan", "halo iyan", "hai yan", "hai iyan", "haloian")
        val cleaned = text.lowercase().trim()
        return wakeWords.firstOrNull { cleaned.contains(it) }
    }

    private fun tryDirectCommand(text: String): Boolean {
        var executed = false
        val cleaned = text.lowercase().trim()
        val nextP = listOf("next song", "next", "ganti lagu", "skip", "lewat")
        val prevP = listOf("previous", "kembali", "balik", "lagu sebelumnya", "sebelumnya")
        val playP = listOf("putar", "mainkan", "lanjut", "play")
        val pauseP = listOf("berhenti", "pause", "stop", "diem", "diam")

        val action = when {
            nextP.any { cleaned.contains(it) } -> {
                controller?.seekToNext()
                logCmd("next", "seekToNext", cleaned)
                "Lagu berikutnya"
            }
            prevP.any { cleaned.contains(it) } -> {
                controller?.seekToPrevious()
                logCmd("prev", "seekToPrevious", cleaned)
                "Kembali ke lagu sebelumnya"
            }
            playP.any { cleaned.contains(it) } -> {
                controller?.play()
                logCmd("play", "play", cleaned)
                "Baik, diputar"
            }
            pauseP.any { cleaned.contains(it) } -> {
                controller?.pause()
                logCmd("pause", "pause", cleaned)
                "Baik, dijeda"
            }
            else -> null
        }

        if (action != null) {
            executed = true
            val response = action
            serviceScope.launch {
                delay(200)
                speak(response)
            }
        }
        return executed
    }

    private fun executeCommand(text: String) {
        val executed = tryDirectCommand(text)
        if (!executed) {
            logCmd("unknown", "no_match", text)
            serviceScope.launch {
                delay(200)
                speak("Maaf, perintah tidak dikenal")
            }
        }
    }

    private fun startListening() {
        if (!isListening) {
            log("Start listening")
            speechRecognizer?.startListening(recognizerIntent)
        }
    }

    private fun scheduleRestart() {
        serviceScope.launch {
            delay(300)
            restartListening()
        }
    }

    private fun restartListening() {
        log("Restart listening (errors=$errorCount)")
        speechRecognizer?.cancel()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        log("Service destroy")
        commandTimeoutJob?.cancel()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
