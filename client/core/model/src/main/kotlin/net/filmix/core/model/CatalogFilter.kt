package net.filmix.core.model

/** One selectable value in a filter group. */
data class FilterOption(val id: Int, val label: String)

/**
 * What a filter group shows while it is collapsed.
 *
 * The order is **stable under selection**: choosing an option already on screen
 * must not move it. The chips are laid out positionally, so a reorder leaves
 * focus on the index rather than on the chip — selecting Франция moved it to the
 * front and left the ring sitting on Корея, so a second press toggled the wrong
 * country. Selections from outside the preview are appended instead, which keeps
 * them visible, and undoable, without shifting anything already there.
 *
 * [pinned] names the preview outright, by id, for a group whose own order is
 * useless to a reader — countries arrive alphabetically, so the first [limit] of
 * 209 were Австралия, Австрия, Азербайджан, Албания. Ids in [pinned] that the
 * backend did not return are skipped rather than faked. With no [pinned] the
 * preview is the first [limit] options. [limit] caps the preview, never the
 * selections appended to it: a selected value that is invisible while collapsed
 * cannot be undone.
 */
fun previewOptions(
    options: List<FilterOption>,
    selected: Set<Int>,
    pinned: List<Int> = emptyList(),
    limit: Int = Int.MAX_VALUE,
): List<FilterOption> {
    val preview = if (pinned.isEmpty()) {
        options.take(limit)
    } else {
        pinned.mapNotNull { id -> options.firstOrNull { it.id == id } }
    }
    val shown = preview.mapTo(mutableSetOf()) { it.id }
    return preview + options.filter { it.id in selected && it.id !in shown }
}

/**
 * The choices `/api/v2/filter_list` offers. Sections are the coarse content
 * type (Фильмы/Сериалы/Мультфильмы); categories are genres.
 */
data class FilterOptions(
    val sections: List<FilterOption> = emptyList(),
    val genres: List<FilterOption> = emptyList(),
    val countries: List<FilterOption> = emptyList(),
    val years: List<FilterOption> = emptyList(),
    val voices: List<FilterOption> = emptyList(),
) {
    val isEmpty: Boolean
        get() = sections.isEmpty() && genres.isEmpty() && countries.isEmpty() &&
            years.isEmpty() && voices.isEmpty()
}

/** Quality flags, which are bare tokens rather than id-prefixed ones. */
enum class QualityFilter(val token: String, val label: String) {
    Hd("qh", "HD"),
    FullHd("q2", "1080+"),
    UltraHd("q4", "4K"),
    Hdr("h1", "HDR"),
    Vision("hv", "Dolby Vision"),
}

/**
 * A catalog filter selection.
 *
 * Serialises to the backend's `filter` parameter: id-prefixed tokens joined by
 * `-`, e.g. `g3-c53-y2020-q4`. Prefixes come from the reference app's builder
 * (`gr.java`) and each was verified against the live API.
 */
data class CatalogFilter(
    val sections: Set<Int> = emptySet(),
    val genres: Set<Int> = emptySet(),
    val countries: Set<Int> = emptySet(),
    val years: Set<Int> = emptySet(),
    val voices: Set<Int> = emptySet(),
    val qualities: Set<QualityFilter> = emptySet(),
) {
    val isEmpty: Boolean
        get() = sections.isEmpty() && genres.isEmpty() && countries.isEmpty() &&
            years.isEmpty() && voices.isEmpty() && qualities.isEmpty()

    val activeCount: Int
        get() = sections.size + genres.size + countries.size + years.size +
            voices.size + qualities.size

    /** Null when nothing is selected, so the caller can omit the parameter. */
    fun toApiValue(): String? {
        if (isEmpty) return null
        val tokens = buildList {
            sections.sorted().forEach { add("s$it") }
            genres.sorted().forEach { add("g$it") }
            countries.sorted().forEach { add("c$it") }
            years.sortedDescending().forEach { add("y$it") }
            voices.sorted().forEach { add("t$it") }
            // Order follows the reference app: qh, q2, q4, h1, hv.
            QualityFilter.entries.filter { it in qualities }.forEach { add(it.token) }
        }
        return tokens.joinToString("-")
    }

    fun toggleSection(id: Int) = copy(sections = sections.toggle(id))
    fun toggleGenre(id: Int) = copy(genres = genres.toggle(id))
    fun toggleCountry(id: Int) = copy(countries = countries.toggle(id))
    fun toggleYear(id: Int) = copy(years = years.toggle(id))
    fun toggleVoice(id: Int) = copy(voices = voices.toggle(id))
    fun toggleQuality(value: QualityFilter) = copy(qualities = qualities.toggle(value))

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (contains(value)) this - value else this + value
}
