package net.filmix.core.model

/**
 * A language the voice search can be told to listen in.
 *
 * A null [tag] means "follow the device", which is what the client did
 * unconditionally before: it passes the system locale to the recogniser. That
 * is the safest option on a device whose recogniser has data for one language
 * only, and the wrong default for this client — the catalog is Russian and the
 * TVs it runs on are shipped as en-US.
 */
data class VoiceLanguage(
    val tag: String?,
    val label: String,
)

/**
 * What Настройки offers. Deliberately short: every extra entry is a language
 * the recogniser may not have data for, and one that fails does so by
 * capturing audio and returning nothing, which reads as a broken microphone.
 */
val voiceLanguages: List<VoiceLanguage> = listOf(
    VoiceLanguage("ru-RU", "Русский"),
    VoiceLanguage("en-US", "English"),
    VoiceLanguage(null, "Как в системе"),
)

/** The tag to hand the recogniser: the stored choice, else the device's own. */
fun resolveVoiceLanguage(stored: String?, systemTag: String): String =
    stored?.trim()?.takeIf { it.isNotEmpty() } ?: systemTag

/**
 * The two-or-three letter code shown under the microphone, so the language in
 * force is visible without opening Настройки. Derived from the tag rather than
 * stored beside the label, because the resolved system language has no entry in
 * [voiceLanguages] to read a label from.
 */
fun voiceLanguageBadge(tag: String): String =
    tag.substringBefore('-')
        .substringBefore('_')
        .trim()
        .uppercase()
        .take(3)
        .ifEmpty { "?" }
