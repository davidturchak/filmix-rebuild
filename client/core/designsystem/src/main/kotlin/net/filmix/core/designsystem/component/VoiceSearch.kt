package net.filmix.core.designsystem.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * In-app voice input, driven by [SpeechRecognizer] rather than
 * ACTION_RECOGNIZE_SPEECH.
 *
 * The intent route looks correct and does nothing useful on Google TV: it
 * opens the system search overlay with an inactive microphone and never
 * listens. Voice in other TV apps comes from the remote's Assistant button,
 * which is a system flow an app cannot invoke. Driving SpeechRecognizer
 * directly — with RECORD_AUDIO — is what actually captures audio here, and is
 * what the reference app did.
 */
class VoiceSearchController internal constructor(
    val available: Boolean,
    private val listeningState: State<Boolean>,
    private val errorState: State<Int?>,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
) {
    val listening: Boolean get() = listeningState.value

    /** Last recogniser error code, or null. See [describeError]. */
    val lastError: Int? get() = errorState.value

    fun start() = if (available) onStart() else Unit
    fun stop() = onStop()
}

@Composable
fun rememberVoiceSearch(
    prompt: String,
    languageTag: String = Locale.getDefault().toLanguageTag(),
    onResult: (String) -> Unit,
): VoiceSearchController {
    val context = LocalContext.current
    val available = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }

    val listening = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<Int?>(null) }
    val recognizer = remember(context, available) {
        if (available) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer?.destroy() }
    }

    val startListening = remember(recognizer, prompt, languageTag) {
        {
            error.value = null
            listening.value = true
            recognizer?.setRecognitionListener(
                listener(listening, error) { spoken -> onResult(spoken) },
            )
            recognizer?.startListening(listenIntent(context, prompt, languageTag))
            Unit
        }
    }

    // RECORD_AUDIO is a runtime permission; ask on first use rather than at
    // launch, so the prompt has obvious context.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startListening() }

    val start = remember(startListening, context) {
        {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startListening() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    return remember(available, start, recognizer) {
        VoiceSearchController(
            available = available,
            listeningState = listening,
            errorState = error,
            onStart = start,
            onStop = {
                recognizer?.stopListening()
                listening.value = false
            },
        )
    }
}

private fun listener(
    listening: MutableState<Boolean>,
    error: MutableState<Int?>,
    onResult: (String) -> Unit,
) = object : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { listening.value = false }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onError(code: Int) {
        listening.value = false
        error.value = code
    }

    override fun onResults(results: Bundle?) {
        listening.value = false
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(onResult)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
}

private fun listenIntent(context: Context, prompt: String, languageTag: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // The device locale, not a hardcoded one: forcing a language the
        // recogniser has no data for makes it capture and return nothing.
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

/** Human-readable reason, for surfacing a failure instead of silently doing nothing. */
fun describeError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука"
    SpeechRecognizer.ERROR_CLIENT -> "Ошибка распознавания"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Нет сети"
    SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание занято"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не услышана"
    else -> "Голосовой поиск недоступен"
}
