package net.filmix.core.model

/**
 * A catalog title. Field names follow the Filmix API's `/api/v2/post/<id>` and
 * `/api/v2/catalog` payloads, which the decompiled reference app parses in
 * `defpackage/k3ZAnV2Mlo2TsX18.java`.
 */
data class Post(
    val id: Int,
    val section: Int = 0,
    val altName: String = "",
    val title: String,
    val originalTitle: String = "",
    val year: Int = 0,
    val posterUrl: String? = null,
    val quality: String? = null,
    val date: String = "",
    val duration: Int = 0,
    val shortStory: String = "",
    val kpRating: String? = null,
    val imdbRating: String? = null,
    val countries: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val favorited: Boolean = false,
    val watchLater: Boolean = false,
    val lastEpisode: LastEpisode? = null,
) {
    /** Sections 7, 714 and 93 are series in the upstream API. */
    val isSeries: Boolean get() = section in SERIES_SECTIONS

    private companion object {
        val SERIES_SECTIONS = setOf(7, 714, 93)
    }
}

data class LastEpisode(val season: Int, val episode: Int)

/** One selectable source, parsed out of the API's raw `translation` string. */
data class VideoSource(
    val rawTranslation: String,
    val templateUrl: String,
    val qualities: List<Int>,
) {
    val bestQuality: Int? get() = qualities.maxOrNull()

    fun urlFor(quality: Int): String = templateUrl.format(quality)
}
