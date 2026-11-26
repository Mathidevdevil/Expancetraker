package com.spendwise.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceSpeechState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val finalTranscript: String? = null,
    val rmsDb: Float = 0f,
    val error: VoiceSpeechError? = null,
    val isUsingOnDevice: Boolean = false
)

/**
 * Wraps Android SpeechRecognizer cleanly without storing any audio recordings.
 * Prefers on-device recognition on Android 13+ (API 33) when available, falling back
 * to the platform default SpeechRecognizer otherwise.
 */
class VoiceSpeechManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(VoiceSpeechState())
    val state: StateFlow<VoiceSpeechState> = _state.asStateFlow()

    private fun createRecognizer(): Pair<SpeechRecognizer?, Boolean> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val onDeviceAvailable = runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            }.getOrDefault(false)
            if (onDeviceAvailable) {
                val onDevice = runCatching {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }.getOrNull()
                if (onDevice != null) {
                    return onDevice to true
                }
            }
        }
        val defaultAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)
        if (!defaultAvailable) {
            return null to false
        }
        val standard = runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
        return standard to false
    }

    fun startListening(languageTag: String) {
        stopAndReleaseRecognizer()

        val (recognizer, onDevice) = createRecognizer()
        if (recognizer == null) {
            _state.value = VoiceSpeechState(
                isListening = false,
                error = VoiceSpeechError(
                    type = VoiceSpeechErrorType.UNAVAILABLE,
                    message = "Speech recognition service is not available on this device. Please enter manually."
                )
            )
            return
        }

        speechRecognizer = recognizer
        _state.value = VoiceSpeechState(
            isListening = true,
            partialText = "",
            finalTranscript = null,
            rmsDb = 0f,
            error = null,
            isUsingOnDevice = onDevice
        )

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(isListening = true, error = null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Intentionally ignored: never persist raw audio.
            }

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false)
            }

            override fun onError(error: Int) {
                val mapped = VoiceTransactionParser.mapAndroidSpeechError(error)
                _state.value = _state.value.copy(
                    isListening = false,
                    error = mapped
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                if (best.isBlank()) {
                    _state.value = _state.value.copy(
                        isListening = false,
                        error = VoiceTransactionParser.mapAndroidSpeechError(SpeechRecognizer.ERROR_NO_MATCH)
                    )
                } else {
                    _state.value = _state.value.copy(
                        isListening = false,
                        partialText = best,
                        finalTranscript = best,
                        error = null
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotBlank()) {
                    _state.value = _state.value.copy(partialText = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            _state.value = _state.value.copy(
                isListening = false,
                error = VoiceSpeechError(
                    type = VoiceSpeechErrorType.UNKNOWN,
                    message = "Could not start speech recognizer. Please enter manually."
                )
            )
        }
    }

    fun cancelListening() {
        runCatching { speechRecognizer?.cancel() }
        stopAndReleaseRecognizer()
        _state.value = _state.value.copy(isListening = false)
    }

    fun setPermissionDeniedError() {
        stopAndReleaseRecognizer()
        _state.value = VoiceSpeechState(
            isListening = false,
            error = VoiceTransactionParser.mapAndroidSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        )
    }

    fun clearErrorAndTranscript() {
        _state.value = VoiceSpeechState()
    }

    private fun stopAndReleaseRecognizer() {
        runCatching {
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun destroy() {
        stopAndReleaseRecognizer()
    }
}
