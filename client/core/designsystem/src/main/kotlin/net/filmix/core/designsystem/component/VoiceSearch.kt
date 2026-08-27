package net.filmix.core.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Voice input via whatever recogniser the device provides.
 *
 * On Google TV this is Katniss, which uses the microphone in the *remote* —
 * the TV itself declares no `android.hardware.microphone` feature, so the app
 * must never require it or it would be filtered off exactly the devices where
 * voice matters most. The button is simply hidden where nothing handles the
 * intent.
 */
class VoiceSearchController internal constructor(
    val available: Boolean,
    private val launch: () -> Unit,
) {
    fun start() = if (available) launch() else Unit
}

@Composable
fun rememberVoiceSearch(
    prompt: String,
    languageTag: String = "ru-RU",
    onResult: (String) -> Unit,
): VoiceSearchController {
    val context = LocalContext.current
    val intent = remember(prompt, languageTag, context) {
        recognizerIntent(prompt, languageTag).targetedAtRealRecognizer(context.packageManager)
    }
    val available = remember(intent, context) {
        intent.resolveActivity(context.packageManager) != null
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(onResult)
    }

    return remember(available, intent) {
        VoiceSearchController(available) { runCatching { launcher.launch(intent) } }
    }
}

/**
 * Pins the intent to a package that actually provides a [RecognitionService].
 *
 * On this Google TV two activities claim ACTION_RECOGNIZE_SPEECH — Katniss and,
 * unexpectedly, the Google TTS engine — so an untargeted intent opens a chooser
 * and makes the user disambiguate speech-to-text from text-to-speech. Declaring
 * a RecognitionService is the meaningful test of which one can actually listen.
 */
private fun Intent.targetedAtRealRecognizer(pm: PackageManager): Intent {
    val handlers = pm.queryIntentActivities(this, 0).map { it.activityInfo.packageName }.toSet()
    if (handlers.size <= 1) return this
    val recognisers = pm
        .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .map { it.serviceInfo.packageName }
        .toSet()
    val best = handlers.firstOrNull { it in recognisers } ?: return this
    return apply { setPackage(best) }
}

private fun recognizerIntent(prompt: String, languageTag: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

/** Exposed so a caller can decide layout before composing the launcher. */
fun isVoiceSearchAvailable(context: Context): Boolean =
    recognizerIntent("", "ru-RU").resolveActivity(context.packageManager) != null
