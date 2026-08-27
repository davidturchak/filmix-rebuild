package net.filmix.core.model

/**
 * Catalog sort options, with the exact `orderby` values the backend accepts.
 *
 * Taken from the reference app's sort menu (`MainActivity` handler) and each
 * verified against the live API — all six return HTTP 200 with a genuinely
 * different ordering.
 */
enum class SortOrder(val apiValue: String, val label: String) {
    Date("date", "По дате"),
    Popularity("rating", "По популярности"),
    Imdb("im_rating", "По IMDb"),
    Kinopoisk("kp_rating", "По КиноПоиску"),
    Year("year", "По году"),
    Comments("comm_num", "По обсуждаемости"),
    ;

    companion object {
        val Default = Date

        fun fromApiValue(value: String?): SortOrder =
            entries.firstOrNull { it.apiValue == value } ?: Default
    }
}

enum class SortDirection(val apiValue: String, val label: String) {
    Desc("desc", "По убыванию"),
    Asc("asc", "По возрастанию"),
    ;

    fun toggled(): SortDirection = if (this == Desc) Asc else Desc

    companion object {
        val Default = Desc
    }
}
