package net.filmix.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Pins the shape of BUILD/latest.json against the clients already installed.
 *
 * The manifest is read by every version ever released, not just the current
 * one, and a client that cannot parse it can never update itself again — the
 * failure is unrecoverable without someone sideloading by hand. So a released
 * manifest must stay readable by the oldest DTO in the field, and new keys may
 * only ever be added alongside the old ones.
 */
class ManifestContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The manifest as clients up to 0.6.7 declare it — deliberately a copy, so
     * that editing the real DTO cannot quietly redefine what those clients
     * expect.
     */
    @Serializable
    private data class LegacyManifestDto(
        val versionCode: Int = 0,
        val versionName: String = "",
        val commit: String = "",
        val apkUrl: String = "",
        val sizeBytes: Long = 0,
        val sha256: String = "",
        val notes: String = "",
    )

    private val withChangelog = """
        {
          "versionCode": 85,
          "versionName": "0.6.8",
          "commit": "abc1234",
          "apkUrl": "https://example.invalid/app.apk",
          "sizeBytes": 2987257,
          "sha256": "df0f",
          "notes": "Что нового",
          "changelog": [
            { "versionCode": 85, "versionName": "0.6.8", "notes": ["Что нового"] },
            { "versionCode": 83, "versionName": "0.6.7", "notes": ["Проверка при запуске"] }
          ]
        }
    """.trimIndent()

    @Test
    fun `a client released before the changelog still parses a manifest with one`() {
        val legacy = json.decodeFromString<LegacyManifestDto>(withChangelog)
        assertEquals(85, legacy.versionCode)
        assertEquals("0.6.8", legacy.versionName)
        // The key it actually acts on: unchanged in type and meaning.
        assertEquals("Что нового", legacy.notes)
        assertEquals("df0f", legacy.sha256)
    }

    @Test
    fun `the published manifest is readable by those clients too`() {
        // Guards the real artefact, not just the shape in this file. The test
        // runs with the module as its working directory, so BUILD/ is three
        // levels up — a wrong path here would make this pass without reading
        // anything, so a missing file fails rather than skips.
        val published = File("../../../BUILD/latest.json")
        assert(published.exists()) { "expected the published manifest at ${published.absolutePath}" }

        val legacy = json.decodeFromString<LegacyManifestDto>(published.readText())
        assert(legacy.versionCode > 0) { "published manifest has no versionCode" }
        assert(legacy.apkUrl.isNotEmpty()) { "published manifest has no apkUrl" }
        assert(legacy.sha256.isNotEmpty()) { "published manifest has no sha256" }
    }
}
