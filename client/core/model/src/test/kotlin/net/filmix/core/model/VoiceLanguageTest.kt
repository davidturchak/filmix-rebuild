package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguageTest {

    @Test
    fun `a stored choice wins over the device locale`() {
        assertEquals("ru-RU", resolveVoiceLanguage("ru-RU", "en-US"))
    }

    @Test
    fun `no stored choice follows the device`() {
        assertEquals("en-US", resolveVoiceLanguage(null, "en-US"))
    }

    @Test
    fun `a blank stored choice follows the device rather than asking for nothing`() {
        assertEquals("en-US", resolveVoiceLanguage("", "en-US"))
        assertEquals("en-US", resolveVoiceLanguage("   ", "en-US"))
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
    fun `the offered list carries exactly one follow-the-device entry, last`() {
        assertEquals(1, voiceLanguages.count { it.tag == null })
        assertEquals(null, voiceLanguages.last().tag)
        assertTrue(voiceLanguages.all { it.label.isNotBlank() })
    }
}
