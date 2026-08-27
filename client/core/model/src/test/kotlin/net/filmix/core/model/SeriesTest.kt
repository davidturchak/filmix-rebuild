package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are real shapes from GET /api/v2/post/141341 (Внешние отмели). */
class SeriesTest {

    private val link =
        "https://nl205.cdnsqu.com/s/abc/Outer-Banks-USA-20-RHS/s04e01_%s.mp4"

    @Test
    fun `episode links are already templated, not bracketed`() {
        val source = StreamLink.fromTemplate("LostFilm", link, listOf(1080, 480, 720))
        assertNotNull(source)
        assertEquals(listOf(1080, 720, 480), source!!.qualities)
        assertEquals(
            "https://nl205.cdnsqu.com/s/abc/Outer-Banks-USA-20-RHS/s04e01_720.mp4",
            source.urlFor(720),
        )
    }

    @Test
    fun `the movie parser cannot read an episode link`() {
        // Guards the reason fromTemplate exists at all.
        assertNull(StreamLink.parse("LostFilm", link))
    }

    @Test
    fun `a link without a placeholder is rejected`() {
        assertNull(StreamLink.fromTemplate("x", "https://h/s01e01_720.mp4", listOf(720)))
    }

    @Test
    fun `no qualities means nothing playable`() {
        assertNull(StreamLink.fromTemplate("x", link, emptyList()))
    }

    @Test
    fun `season and episode keys sort numerically, not lexically`() {
        val keys = listOf("10", "2", "1", "5")
        assertEquals(listOf("1", "2", "5", "10"), keys.sortedWith(numericKeyComparator()))
    }

    @Test
    fun `non-numeric keys sort last rather than crashing`() {
        val keys = listOf("2", "спецвыпуск", "1")
        assertEquals(listOf("1", "2", "спецвыпуск"), keys.sortedWith(numericKeyComparator()))
    }

    @Test
    fun `labels read naturally`() {
        assertEquals("Сезон 4", Season("4", emptyList()).label)
        assertEquals(
            "3 серия",
            Episode("3", VideoSource("t", link, listOf(720))).label,
        )
    }
}
