package com.spendwise.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * Wraps Android SpeechRecognizer on the main thread without storing any audio recordings.
 * Strategy:
 * 1. On Android 13+, tries on-device SpeechRecognizer first when available.
 * 2. If on-device fails due to missing offline language packs or service errors,
 *    automatically falls back to the platform default SpeechRecognizer.
 * 3. Promotes captured partial results instead of discarding them on timeout/no-match.
 */
class VoiceSpeechManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeLanguageTag: String = "en-IN"
    private var allowOnDeviceAttempt: Boolean = true
    private var hasRetriedStandardForSession: Boolean = false
    private var sessionToken: Int = 0

    private val _state = MutableStateFlow(VoiceSpeechState())
    val state: StateFlow<VoiceSpeechState> = _state.asStateFlow()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun createRecognizer(preferOnDevice: Boolean): Pair<SpeechRecognizer?, Boolean> {
        if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        runOnMain {
            activeLanguageTag = languageTag
            allowOnDeviceAttempt = true
            hasRetriedStandardForSession = false
            startInternal(languageTag = languageTag, preferOnDevice = true)
        }
    }

    private fun startInternal(languageTag: String, preferOnDevice: Boolean) {
        stopAndReleaseRecognizerInternal()
        sessionToken += 1
        val currentToken = sessionToken

        val (recognizer, onDevice) = createRecognizer(preferOnDevice)
        if (recognizer == null) {
            _state.value = VoiceSpeechState(
                isListening = false,
                error = VoiceSpeechError(
                    type = VoiceSpeechErrorType.UNAVAILABLE,
                    message = "Inline speech service is unavailable on this device."
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
                if (currentToken != sessionToken) return
                _state.value = _state.value.copy(isListening = true, error = null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (currentToken != sessionToken) return
                _state.value = _state.value.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Intentionally ignored: never persist raw audio.
            }

            override fun onEndOfSpeech() {
                if (currentToken != sessionToken) return
                _state.value = _state.value.copy(isListening = false)
            }

            override fun onError(error: Int) {
                if (currentToken != sessionToken) return

                // If partial words were already captured and recognizer timed out / ended, keep them!
                val capturedPartial = _state.value.partialText.trim()
                if (capturedPartial.isNotEmpty() && (error == 6 || error == 7 || error == 5)) {
                    stopAndReleaseRecognizerInternal()
                    _state.value = _state.value.copy(
                        isListening = false,
                        finalTranscript = capturedPartial,
                        error = null
                    )
                    return
                }

                // Automatic silent fallback from on-device SpeechRecognizer to default SpeechRecognizer
                // when the offline pack / language is unavailable or on-device service fails.
                if (onDevice && !hasRetriedStandardForSession && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    hasRetriedStandardForSession = true
                    allowOnDeviceAttempt = false
                    startInternal(languageTag = activeLanguageTag, preferOnDevice = false)
                    return
                }

                stopAndReleaseRecognizerInternal()
                val mapped = VoiceTransactionParser.mapAndroidSpeechError(error)
                _state.value = _state.value.copy(
                    isListening = false,
                    error = mapped
                )
            }

            override fun onResults(results: Bundle?) {
                if (currentToken != sessionToken) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                    .ifBlank { _state.value.partialText.trim() }

                stopAndReleaseRecognizerInternal()
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
                if (currentToken != sessionToken) return
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
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        runCatching {
            recognizer.startListening(intent)
        }.onFailure {
            if (onDevice && !hasRetriedStandardForSession) {
                hasRetriedStandardForSession = true
                startInternal(languageTag = activeLanguageTag, preferOnDevice = false)
            } else {
                stopAndReleaseRecognizerInternal()
                _state.value = _state.value.copy(
                    isListening = false,
                    error = VoiceSpeechError(
                        type = VoiceSpeechErrorType.UNAVAILABLE,
                        message = "Could not start inline speech recognizer. Try system voice input."
                    )
                )
            }
        }
    }

    fun finishListening() {
        runOnMain {
            val captured = _state.value.partialText.trim()
            if (captured.isNotEmpty()) {
                sessionToken += 1
                runCatching { speechRecognizer?.cancel() }
                stopAndReleaseRecognizerInternal()
                _state.value = _state.value.copy(
                    isListening = false,
                    finalTranscript = captured,
                    error = null
                )
            } else {
                runCatching { speechRecognizer?.stopListening() }
            }
        }
    }

    fun cancelListening() {
        runOnMain {
            sessionToken += 1
            runCatching { speechRecognizer?.cancel() }
            stopAndReleaseRecognizerInternal()
            _state.value = _state.value.copy(isListening = false)
        }
    }

    fun setPermissionDeniedError() {
        runOnMain {
            sessionToken += 1
            stopAndReleaseRecognizerInternal()
            _state.value = VoiceSpeechState(
                isListening = false,
                error = VoiceTransactionParser.mapAndroidSpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            )
        }
    }

    fun clearErrorAndTranscript() {
        runOnMain {
            _state.value = VoiceSpeechState()
        }
    }

    private fun stopAndReleaseRecognizerInternal() {
        runCatching {
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun destroy() {
        runOnMain {
            sessionToken += 1
            stopAndReleaseRecognizerInternal()
        }
    }
}
