package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fixtures are real strings captured from `GET /api/v2/post/186499`, including
 * the empty CSV entries the upstream parser skips.
 */
class StreamLinkTest {

    private val link1080 =
        "https://nl221.werkecdn.me/s/abc/UHD_1313/" +
            "Batman.Knightfall.Part.1.2026.MVO.ru.WinMedia.WEBDL.1080pp_[,1440,1080,720,480,].mp4"

    private val link4k =
        "https://nl221.werkecdn.me/s/abc/UHD_1313/" +
            "Batman.Knightfall.Part.1.2026.D.ru.HDRezka.4K.SDR.WEBDLHDRezkarip.2160p_[2160,,1080,720,480,].mp4"

    @Test
    fun `skips empty csv entries and sorts descending`() {
        val source = StreamLink.parse("MVO [1080+, ru, WinMedia]", link1080)!!
        assertEquals(listOf(1440, 1080, 720, 480), source.qualities)
    }

    @Test
    fun `leading quality before empty entry is kept`() {
        val source = StreamLink.parse("Дубляж [4K, SDR, ru, HDRezka]", link4k)!!
        assertEquals(listOf(2160, 1080, 720, 480), source.qualities)
    }

    @Test
    fun `template substitutes the chosen height`() {
        val source = StreamLink.parse("MVO", link1080)!!
        assertEquals(
            "https://nl221.werkecdn.me/s/abc/UHD_1313/" +
                "Batman.Knightfall.Part.1.2026.MVO.ru.WinMedia.WEBDL.1080pp_1080.mp4",
            source.urlFor(1080),
        )
    }

    @Test
    fun `preferred quality wins when offered, else the best`() {
        val source = StreamLink.parse("MVO", link1080)!!
        assertEquals(720, StreamLink.selectQuality(source, preferred = 720))
        assertEquals(1440, StreamLink.selectQuality(source, preferred = 2160))
        assertEquals(1440, StreamLink.selectQuality(source, preferred = null))
    }

    @Test
    fun `link without a quality bracket is rejected`() {
        assertNull(StreamLink.parse("MVO", "https://host/plain.mp4"))
    }

    @Test
    fun `resume key is stable across qualities`() {
        val a = StreamLink.resumeKey("https://h/s01e02_720.mp4")
        val b = StreamLink.resumeKey("https://h/s01e02_2160.mp4")
        assertEquals(a, b)
        assertEquals("s01e02_%s.mp4", a)
    }

    @Test
    fun `resume key survives the rotating CDN token and host`() {
        // Real shape: /s/<token>/ is a signed segment that rotates within
        // minutes between fetches of the same post; the host node can move too.
        val a = StreamLink.resumeKey(
            "https://nl205.cdnsqu.com/s/FHAwtAio9sbLVVh5.abc/Outer-Banks-USA-20-RHS/s04e01_720.mp4",
        )
        val b = StreamLink.resumeKey(
            "https://nl209.cdnsqu.com/s/FHdMXyNkPbZ5BQcE.xyz/Outer-Banks-USA-20-RHS/s04e01_1080.mp4",
        )
        assertEquals(a, b)
        assertEquals("Outer-Banks-USA-20-RHS/s04e01_%s.mp4", a)
    }

    @Test
    fun `resume keys of different episodes stay distinct`() {
        val e1 = StreamLink.resumeKey("https://h/s/tok/Folder/s04e01_720.mp4")
        val e2 = StreamLink.resumeKey("https://h/s/tok/Folder/s04e02_720.mp4")
        assertEquals(false, e1 == e2)
    }

    @Test
    fun `translation splits into voice and tags`() {
        val parsed = ParsedTranslation.from("Дубляж [4K, SDR, ru, HDRezka]")
        assertEquals("Дубляж", parsed.voice)
        assertEquals(listOf("4K", "SDR", "ru", "HDRezka"), parsed.tags)
    }

    @Test
    fun `translation without brackets degrades gracefully`() {
        val parsed = ParsedTranslation.from("Оригинал")
        assertEquals("Оригинал", parsed.voice)
        assertEquals(emptyList<String>(), parsed.tags)
    }
}
