package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterPreviewTest {

    /** Countries as the backend sends them: alphabetical, with real ids. */
    private val countries = listOf(
        FilterOption(53, "Австралия"),
        FilterOption(72, "Австрия"),
        FilterOption(73, "Азербайджан"),
        FilterOption(3, "Германия"),
        FilterOption(18, "Израиль"),
        FilterOption(12, "Корея"),
        FilterOption(25, "Корея Южная"),
        FilterOption(6, "Россия"),
        FilterOption(2, "США"),
        FilterOption(8, "Франция"),
    )

    private val pinned = listOf(6, 18, 2, 12, 8, 3)

    @Test
    fun `pinned preview keeps the given order, not the backend's`() {
        val preview = previewOptions(countries, selected = emptySet(), pinned = pinned)
        assertEquals(
            listOf("Россия", "Израиль", "США", "Корея", "Франция", "Германия"),
            preview.map { it.label },
        )
    }

    /** "Корея Южная" shares a prefix with "Корея" and must not ride along. */
    @Test
    fun `pinned preview matches on id, not label`() {
        val preview = previewOptions(countries, selected = emptySet(), pinned = listOf(12))
        assertEquals(listOf("Корея"), preview.map { it.label })
    }

    @Test
    fun `a selected option leads the preview and appears once`() {
        val preview = previewOptions(countries, selected = setOf(2), pinned = pinned)
        assertEquals(
            listOf("США", "Россия", "Израиль", "Корея", "Франция", "Германия"),
            preview.map { it.label },
        )
    }

    /** A choice made while expanded has to stay visible after collapsing. */
    @Test
    fun `a selected option outside the pinned set still shows`() {
        val preview = previewOptions(countries, selected = setOf(72), pinned = pinned)
        assertEquals("Австрия", preview.first().label)
        assertEquals(7, preview.size)
    }

    /** The backend drops countries from time to time; a stale id is not a crash. */
    @Test
    fun `an unknown pinned id is skipped`() {
        val preview = previewOptions(countries, selected = emptySet(), pinned = listOf(6, 9999, 2))
        assertEquals(listOf("Россия", "США"), preview.map { it.label })
    }

    @Test
    fun `an unpinned group previews the first options up to the limit`() {
        val preview = previewOptions(countries, selected = emptySet(), limit = 3)
        assertEquals(listOf("Австралия", "Австрия", "Азербайджан"), preview.map { it.label })
    }

    @Test
    fun `an unpinned group pulls selected options into the limit`() {
        val preview = previewOptions(countries, selected = setOf(8), limit = 3)
        assertEquals(listOf("Франция", "Австралия", "Австрия"), preview.map { it.label })
    }

    @Test
    fun `pinned ignores the limit, since the pinned set is the preview`() {
        val preview = previewOptions(countries, selected = emptySet(), pinned = pinned, limit = 2)
        assertEquals(6, preview.size)
    }
}
