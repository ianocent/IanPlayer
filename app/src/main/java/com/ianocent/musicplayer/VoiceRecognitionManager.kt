package com.ianocent.musicplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceRecognitionManager(
    private val context: Context,
    private val onResults: (String) -> Unit,
    private val onPartialResults: (String) -> Unit,
    private val onError: (Int) -> Unit,
    private val onRmsChanged: (Float) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    /** Fires when speech recognition ends with no useful text — signals caller to try song recognition. */
    private val onSpeechFailed: (() -> Unit)? = null
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var hadPartialResults = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            hadPartialResults = false
            onListeningStateChanged(true)
            onRmsChanged(0f)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged(rmsdB / 10f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            onListeningStateChanged(false)
            // Speech failed — if no partial results came in, suggest song recognition
            if (!hadPartialResults) {
                onSpeechFailed?.invoke()
            } else {
                onError(error)
            }
        }

        override fun onResults(results: Bundle?) {
            onListeningStateChanged(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                // If the recognized text is very short or looks like noise, suggest song recognition
                if (text.length < 3 || text.isBlank()) {
                    onSpeechFailed?.invoke()
                } else {
                    onResults(text)
                }
            } else {
                onSpeechFailed?.invoke()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { list ->
                if (list.isNotEmpty()) {
                    hadPartialResults = true
                    onPartialResults(list[0])
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startListening() {
        hadPartialResults = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nyanyiin atau sebutin lagu")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
    }
}
