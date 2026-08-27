package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Token shapes verified live: g3, g21, s7, c53, y2020, g3-c53, q4 all return 200. */
class CatalogFilterTest {

    @Test
    fun `empty selection produces no parameter`() {
        assertNull(CatalogFilter().toApiValue())
    }

    @Test
    fun `single genre`() {
        assertEquals("g3", CatalogFilter(genres = setOf(3)).toApiValue())
    }

    @Test
    fun `groups are emitted in the reference order`() {
        val filter = CatalogFilter(
            sections = setOf(7),
            genres = setOf(3),
            countries = setOf(53),
            years = setOf(2020),
            voices = setOf(1),
        )
        assertEquals("s7-g3-c53-y2020-t1", filter.toApiValue())
    }

    @Test
    fun `quality flags are bare tokens appended last`() {
        val filter = CatalogFilter(genres = setOf(3), qualities = setOf(QualityFilter.UltraHd))
        assertEquals("g3-q4", filter.toApiValue())
    }

    @Test
    fun `quality flags keep qh q2 q4 h1 hv order regardless of insertion`() {
        val filter = CatalogFilter(
            qualities = setOf(QualityFilter.Vision, QualityFilter.Hd, QualityFilter.UltraHd),
        )
        assertEquals("qh-q4-hv", filter.toApiValue())
    }

    @Test
    fun `multiple values within a group are joined`() {
        assertEquals("g3-g21", CatalogFilter(genres = setOf(21, 3)).toApiValue())
    }

    @Test
    fun `years sort newest first`() {
        assertEquals("y2024-y2020", CatalogFilter(years = setOf(2020, 2024)).toApiValue())
    }

    @Test
    fun `toggle adds then removes`() {
        val once = CatalogFilter().toggleGenre(3)
        assertEquals(setOf(3), once.genres)
        assertEquals(emptySet<Int>(), once.toggleGenre(3).genres)
    }

    @Test
    fun `activeCount spans every group`() {
        val filter = CatalogFilter(
            genres = setOf(3, 21),
            countries = setOf(53),
            qualities = setOf(QualityFilter.UltraHd),
        )
        assertEquals(4, filter.activeCount)
    }
}
