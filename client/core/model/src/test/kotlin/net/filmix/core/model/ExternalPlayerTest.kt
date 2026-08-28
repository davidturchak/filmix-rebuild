package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalPlayerTest {

    @Test
    fun `mx style int extras decode`() {
        val progress = externalProgressFromExtras(mapOf("position" to 65_000, "duration" to 5_400_000))
        assertEquals(ExternalProgress(65_000L, 5_400_000L), progress)
    }

    @Test
    fun `vlc style long extras decode`() {
        val progress = externalProgressFromExtras(
            mapOf("extra_position" to 65_000L, "extra_duration" to 5_400_000L),
        )
        assertEquals(ExternalProgress(65_000L, 5_400_000L), progress)
    }

    @Test
    fun `vlc keys win when both conventions are present`() {
        val progress = externalProgressFromExtras(
            mapOf(
                "extra_position" to 90_000L,
                "extra_duration" to 5_400_000L,
                "position" to 1,
                "duration" to 2,
            ),
        )
        assertEquals(ExternalProgress(90_000L, 5_400_000L), progress)
    }

    @Test
    fun `missing position is nothing reported`() {
        assertNull(externalProgressFromExtras(mapOf("duration" to 5_400_000)))
        assertNull(externalProgressFromExtras(emptyMap()))
    }

    @Test
    fun `zero or negative position is nothing reported`() {
        assertNull(externalProgressFromExtras(mapOf("position" to 0)))
        assertNull(externalProgressFromExtras(mapOf("extra_position" to -1L)))
    }

    @Test
    fun `wrong typed values are ignored`() {
        assertNull(externalProgressFromExtras(mapOf("position" to "65000")))
        val progress = externalProgressFromExtras(
            mapOf("position" to 65_000, "duration" to "not a number"),
        )
        assertEquals(ExternalProgress(65_000L, 0L), progress)
    }

    @Test
    fun `missing duration defaults to zero`() {
        assertEquals(
            ExternalProgress(65_000L, 0L),
            externalProgressFromExtras(mapOf("position" to 65_000)),
        )
    }

    @Test
    fun `player list drops self dedupes and sorts`() {
        val normalised = normalisePlayerList(
            listOf(
                ExternalPlayer("VLC", "org.videolan.vlc"),
                ExternalPlayer("filmix-ng", "dev.turchak.filmixng"),
                ExternalPlayer("MX Player", "com.mxtech.videoplayer.ad"),
                ExternalPlayer("VLC (resume)", "org.videolan.vlc"),
                ExternalPlayer("None", "com.android.tv.frameworkpackagestubs"),
                ExternalPlayer("just Player", "com.brouken.player"),
            ),
            selfPackage = "dev.turchak.filmixng",
        )
        assertEquals(
            listOf(
                ExternalPlayer("just Player", "com.brouken.player"),
                ExternalPlayer("MX Player", "com.mxtech.videoplayer.ad"),
                ExternalPlayer("VLC", "org.videolan.vlc"),
            ),
            normalised,
        )
    }
}
