package net.filmix.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.filmix.core.model.Html
import net.filmix.core.model.LastEpisode
import net.filmix.core.model.PersonRef
import net.filmix.core.model.StreamLink
import net.filmix.core.model.Post

/** `GET http://ap.gnft.pro/server.json` */
@Serializable
data class ServerListDto(
    @SerialName("default_server") val defaultServer: String = "",
    val servers: List<String> = emptyList(),
)

/** `GET /api/v2/token_request` → `{status, code, user_code, expire}` */
@Serializable
data class TokenRequestDto(
    val status: String = "",
    /** The session token. Stored and sent as `user_dev_token`. */
    val code: String = "",
    /** The short code the user types on the website to link this device. */
    @SerialName("user_code") val userCode: String = "",
    /** Unix seconds. */
    val expire: Long = 0,
)

/**
 * `GET /api/v2/user_profile`. Returns a bare `{}` until the device is linked,
 * so [userData] being null *is* the unauthenticated signal.
 */
@Serializable
data class UserProfileDto(
    @SerialName("user_data") val userData: UserDataDto? = null,
)

@Serializable
data class UserDataDto(
    val login: String = "",
    @SerialName("display_name") val displayName: String = "",
    val foto: String = "",
    @SerialName("is_pro") val isPro: Boolean = false,
    @SerialName("is_pro_plus") val isProPlus: Boolean = false,
    @SerialName("pro_date") val proDate: String = "",
    val videoserver: String = "",
    @SerialName("available_servers") val availableServers: Map<String, String> = emptyMap(),
)

/** One entry of `GET /api/v2/catalog`, and the summary shape reused elsewhere. */
@Serializable
data class PostDto(
    val id: Int = 0,
    val section: Int = 0,
    @SerialName("alt_name") val altName: String = "",
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    val year: Int = 0,
    val poster: String? = null,
    val quality: String? = null,
    val date: String = "",
    val duration: Int = 0,
    @SerialName("short_story") val shortStory: String = "",
    @SerialName("kp_rating") val kpRating: String? = null,
    @SerialName("imdb_rating") val imdbRating: String? = null,
    val countries: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val favorited: Boolean = false,
    @SerialName("watch_later") val watchLater: Boolean = false,
    @SerialName("last_episode") val lastEpisode: LastEpisodeDto? = null,
    val rip: String? = null,
    @SerialName("post_url") val postUrl: String = "",
    @SerialName("rate_p") val ratePositive: Int = 0,
    @SerialName("rate_n") val rateNegative: Int = 0,
    @SerialName("found_actors") val foundActors: List<PersonRefDto> = emptyList(),
    val relates: List<RelateDto> = emptyList(),
    @SerialName("player_links") val playerLinks: PlayerLinksDto? = null,
)

@Serializable
data class PersonRefDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
)

@Serializable
data class RelateDto(
    val id: Int = 0,
    @SerialName("alt_name") val altName: String = "",
    val title: String = "",
    val poster: String? = null,
    val year: Int = 0,
    val category: String = "",
)

/**
 * `playlist` is polymorphic: an empty JSON *array* when a title has no episode
 * tree (films, and anything rights-blocked), but an *object* keyed by season
 * when it does. Decoding it into a fixed type fails one case or the other, so
 * it is held as a raw element and interpreted by [PlayerLinksDto.seasons].
 */
@Serializable
data class PlayerLinksDto(
    val movie: List<MovieLinkDto> = emptyList(),
    val playlist: JsonElement? = null,
    val trailer: JsonElement? = null,
)

@Serializable
data class MovieLinkDto(
    val link: String = "",
    val translation: String = "",
)

/**
 * Both fields arrive as strings, and `episode` may be a range covering several
 * releases at once (`"1-4"`, `"13-14"`). Modelling them as Int fails the whole
 * catalog page to parse.
 */
@Serializable
data class LastEpisodeDto(
    val season: String = "",
    val episode: String = "",
    val translation: String = "",
)

/**
 * Notification feed entry. Fields are permissive because the shape has not been
 * observed with real data — the feed is empty for unpaired accounts.
 */
@Serializable
data class NotificationDto(
    val id: Long = 0,
    @SerialName("post_id") val postId: Int = 0,
    val title: String = "",
    val message: String = "",
    val date: String = "",
    val poster: String? = null,
    val read: Boolean = false,
)

fun PostDto.toDomain(): Post = Post(
    id = id,
    section = section,
    altName = altName,
    title = Html.toPlainText(title),
    originalTitle = Html.toPlainText(originalTitle),
    year = year,
    posterUrl = poster,
    quality = quality,
    date = date,
    duration = duration,
    shortStory = Html.toPlainText(shortStory),
    kpRating = kpRating,
    imdbRating = imdbRating,
    // Every free-text field can carry entities — cast names routinely contain
    // things like "J&#233;r&#233;my".
    countries = countries.map(Html::toPlainText),
    categories = categories.map(Html::toPlainText),
    actors = actors.map(Html::toPlainText),
    directors = directors.map(Html::toPlainText),
    favorited = favorited,
    watchLater = watchLater,
    lastEpisode = lastEpisode
        ?.takeIf { it.season.isNotEmpty() || it.episode.isNotEmpty() }
        ?.let { LastEpisode(season = it.season, episode = it.episode) },
    rip = rip,
    postUrl = postUrl,
    ratePositive = ratePositive,
    rateNegative = rateNegative,
    cast = foundActors.map {
        PersonRef(it.id, Html.toPlainText(it.name), Html.toPlainText(it.originalName))
    },
    related = relates.map { relate ->
        Post(
            id = relate.id,
            title = Html.toPlainText(relate.title),
            year = relate.year,
            posterUrl = relate.poster,
            altName = relate.altName,
        )
    },
    // Links without a quality bracket are unplayable by this client, so they
    // are dropped rather than surfaced as broken rows.
    sources = playerLinks?.movie.orEmpty()
        .mapNotNull { StreamLink.parse(it.translation, it.link) },
)
