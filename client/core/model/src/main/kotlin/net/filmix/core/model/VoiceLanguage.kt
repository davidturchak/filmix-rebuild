package net.filmix.core.model

/** A language the voice search can be told to listen in. */
data class VoiceLanguage(
    val tag: String,
    val label: String,
)

/**
 * Follow the device instead of choosing. A sentinel rather than an absent
 * value, because absent now means "has not chosen" — and that resolves to
 * Russian, not to the device.
 */
const val VOICE_LANGUAGE_SYSTEM = "system"

/**
 * What voice search listens in until the user says otherwise.
 *
 * Not the device locale, which is what the client used to pass: the catalog is
 * Russian and the TVs this runs on ship as en-US, so following the device meant
 * searching a Russian catalog in English out of the box. Following the device
 * is still on offer — it is just no longer the default.
 */
const val VOICE_LANGUAGE_DEFAULT = "ru-RU"

/**
 * What Настройки offers. Deliberately short: every extra entry is a language
 * the recogniser may not have data for, and one that fails does so by
 * capturing audio and returning nothing, which reads as a broken microphone.
 */
val voiceLanguages: List<VoiceLanguage> = listOf(
    VoiceLanguage(VOICE_LANGUAGE_DEFAULT, "Русский"),
    VoiceLanguage("en-US", "English"),
    VoiceLanguage(VOICE_LANGUAGE_SYSTEM, "Как в системе"),
)

/**
 * Which entry Настройки shows as selected. Never null, so the chips always
 * agree with what the microphone is actually doing — including before DataStore
 * has emitted anything.
 */
fun selectedVoiceLanguage(stored: String?): String =
    stored?.trim()?.takeIf { it.isNotEmpty() } ?: VOICE_LANGUAGE_DEFAULT

/** The tag to hand the recogniser: never the sentinel, never empty. */
fun resolveVoiceLanguage(stored: String?, systemTag: String): String {
    val choice = selectedVoiceLanguage(stored)
    if (choice != VOICE_LANGUAGE_SYSTEM) return choice
    return systemTag.trim().takeIf { it.isNotEmpty() } ?: VOICE_LANGUAGE_DEFAULT
}

/**
 * The two-or-three letter code shown under the microphone, so the language in
 * force is visible without opening Настройки.
 *
 * Takes a *resolved* tag — [resolveVoiceLanguage]'s answer, never the stored
 * choice, which can be the sentinel and would badge as "SYS".
 */
fun voiceLanguageBadge(tag: String): String =
    tag.substringBefore('-')
        .substringBefore('_')
        .trim()
        .uppercase()
        .take(3)
        .ifEmpty { "?" }
