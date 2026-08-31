package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguageTest {

    @Test
    fun `nothing stored listens in Russian, not the device language`() {
        assertEquals("ru-RU", resolveVoiceLanguage(null, "en-US"))
        assertEquals("ru-RU", selectedVoiceLanguage(null))
    }

    @Test
    fun `a stored choice wins`() {
        assertEquals("en-US", resolveVoiceLanguage("en-US", "ru-RU"))
    }

    @Test
    fun `the system sentinel follows the device`() {
        assertEquals("en-US", resolveVoiceLanguage(VOICE_LANGUAGE_SYSTEM, "en-US"))
        assertEquals("uk-UA", resolveVoiceLanguage(VOICE_LANGUAGE_SYSTEM, "uk-UA"))
    }

    @Test
    fun `a blank stored choice is no choice at all`() {
        assertEquals("ru-RU", resolveVoiceLanguage("", "en-US"))
        assertEquals("ru-RU", resolveVoiceLanguage("   ", "en-US"))
    }

    @Test
    fun `an unusable system tag falls back rather than asking for nothing`() {
        assertEquals("ru-RU", resolveVoiceLanguage(VOICE_LANGUAGE_SYSTEM, ""))
        assertEquals("ru-RU", resolveVoiceLanguage(VOICE_LANGUAGE_SYSTEM, "  "))
    }

    @Test
    fun `the resolved tag is never the sentinel`() {
        val resolved = voiceLanguages.map { resolveVoiceLanguage(it.tag, "en-US") }
        assertTrue(resolved.none { it == VOICE_LANGUAGE_SYSTEM })
    }

    @Test
    fun `the badge is the language subtag, without the region`() {
        assertEquals("RU", voiceLanguageBadge("ru-RU"))
        assertEquals("EN", voiceLanguageBadge("en-US"))
        assertEquals("UK", voiceLanguageBadge("uk"))
    }

    @Test
    fun `the badge copes with underscore tags and casing`() {
        assertEquals("RU", voiceLanguageBadge("ru_RU"))
        assertEquals("EN", voiceLanguageBadge("EN-gb"))
    }

    @Test
    fun `an unusable tag still renders something`() {
        assertEquals("?", voiceLanguageBadge(""))
        assertEquals("?", voiceLanguageBadge("-RU"))
    }

    @Test
    fun `the offered list leads with the default and ends with the device`() {
        assertEquals(VOICE_LANGUAGE_DEFAULT, voiceLanguages.first().tag)
        assertEquals(VOICE_LANGUAGE_SYSTEM, voiceLanguages.last().tag)
        assertEquals(1, voiceLanguages.count { it.tag == VOICE_LANGUAGE_SYSTEM })
        assertTrue(voiceLanguages.all { it.label.isNotBlank() && it.tag.isNotBlank() })
    }
}
