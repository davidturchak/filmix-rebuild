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

    /**
     * The property the chips depend on. They are positional, so an option that
     * moves when it is chosen takes the focus ring off itself and onto whatever
     * lands at its index — one press selects Франция, the next toggles Корея.
     */
    @Test
    fun `selecting a visible option does not move it`() {
        val before = previewOptions(countries, selected = emptySet(), pinned = pinned)
        countries.filter { it.id in pinned }.forEach { option ->
            val after = previewOptions(countries, selected = setOf(option.id), pinned = pinned)
            assertEquals(
                "selecting ${option.label} reordered the preview",
                before.map { it.id },
                after.map { it.id },
            )
        }
    }

    @Test
    fun `selecting a visible option in an unpinned group does not move it`() {
        val before = previewOptions(countries, selected = emptySet(), limit = 4)
        val after = previewOptions(countries, selected = setOf(72), limit = 4)
        assertEquals(before.map { it.id }, after.map { it.id })
    }

    /** A choice made while expanded has to stay visible after collapsing. */
    @Test
    fun `a selected option outside the preview is appended`() {
        val preview = previewOptions(countries, selected = setOf(72), pinned = pinned)
        assertEquals(7, preview.size)
        assertEquals("Австрия", preview.last().label)
        assertEquals(
            listOf("Россия", "Израиль", "США", "Корея", "Франция", "Германия"),
            preview.dropLast(1).map { it.label },
        )
    }

    /** The cap is on the preview, not on what the user has chosen. */
    @Test
    fun `limit never truncates a selection`() {
        val preview = previewOptions(countries, selected = setOf(8, 2), limit = 3)
        assertEquals(
            listOf("Австралия", "Австрия", "Азербайджан", "США", "Франция"),
            preview.map { it.label },
        )
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
    fun `pinned ignores the limit, since the pinned set is the preview`() {
        val preview = previewOptions(countries, selected = emptySet(), pinned = pinned, limit = 2)
        assertEquals(6, preview.size)
    }

    @Test
    fun `an option appears once even when it is both pinned and selected`() {
        val preview = previewOptions(countries, selected = setOf(6), pinned = pinned)
        assertEquals(1, preview.count { it.id == 6 })
        assertEquals(6, preview.size)
    }
}
