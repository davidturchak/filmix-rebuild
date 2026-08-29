package net.filmix.core.model

/**
 * A series episode tree.
 *
 * `player_links.playlist` nests three levels — season, then voice-over, then
 * episode — which is a level deeper than the movie form and easy to get wrong:
 *
 *     "4": { "LostFilm": { "1": { link, qualities, wd }, "2": {...} } }
 *
 * Episode links also differ from movie links: they arrive already templated
 * with `%s` and carry an explicit `qualities` array, rather than encoding the
 * list in a `[...]` bracket that has to be parsed out. Verified across four
 * series and 386 episodes — every one templated, none bracketed.
 */
data class SeriesPlaylist(val seasons: List<Season>) {
    val isEmpty: Boolean get() = seasons.isEmpty()

    companion object {
        val Empty = SeriesPlaylist(emptyList())
    }
}

data class Season(
    val number: String,
    val translations: List<SeriesTranslation>,
) {
    val label: String get() = "Сезон $number"
}

/** One voice-over's episodes for a season; not every translation covers all of them. */
data class SeriesTranslation(
    val name: String,
    val episodes: List<Episode>,
)

data class Episode(
    val number: String,
    /** Reuses the movie playback path — the player only needs a template plus qualities. */
    val source: VideoSource,
) {
    val label: String get() = "$number серия"

    /**
     * Episode links arrive already templated with `%s`, so the quality-agnostic
     * resume key is computable from the template alone — the watched state of
     * an episode never needs to know which quality was played.
     */
    val resumeKey: String get() = StreamLink.resumeKey(source.templateUrl)
}

/**
 * Seasons and episodes arrive as unordered string-keyed maps ("4","5","1"...),
 * so both need sorting numerically rather than lexically — otherwise episode 10
 * lands between 1 and 2.
 */
internal fun numericKeyComparator(): Comparator<String> =
    compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })
