package net.filmix.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import net.filmix.core.model.Avatar
import net.filmix.core.model.Comment
import net.filmix.core.model.FilterOption
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.Html
import net.filmix.core.model.LastEpisode
import net.filmix.core.model.Episode
import net.filmix.core.model.PersonRef
import net.filmix.core.model.Season
import net.filmix.core.model.SeriesPlaylist
import net.filmix.core.model.SeriesTranslation
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
    /** Site score: net of up/down votes. Negative for poorly-received titles. */
    val rating: String? = null,
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

/**
 * The reply to a cast vote: the new totals for the title.
 *
 * Both fields are nullable on purpose. A rejected vote comes back without
 * them — the reference app guards with `has("rate_p") && has("rate_n")` before
 * touching its labels — and `NetworkFactory.json` coerces input values, so
 * non-nullable `Int = 0` fields would turn a refusal into a believable
 * "0 up, 0 down" and wipe the tally on screen.
 */
@Serializable
data class RateDto(
    @SerialName("rate_p") val ratePositive: Int? = null,
    @SerialName("rate_n") val rateNegative: Int? = null,
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
 * `/api/v2/filter_list`. Every group is an object keyed by id, and most keys
 * carry an `f` prefix (`"f53": "Австралия"`) while `sections` does not
 * (`"7": "Сериалы"`) — [toOptions] strips it either way.
 *
 * `years` values are numbers rather than strings, so the value type is a raw
 * element and rendered with [JsonPrimitive.content].
 */
@Serializable
data class FilterListDto(
    val sections: Map<String, JsonPrimitive> = emptyMap(),
    val categories: Map<String, JsonPrimitive> = emptyMap(),
    val countries: Map<String, JsonPrimitive> = emptyMap(),
    val years: Map<String, JsonPrimitive> = emptyMap(),
    val vo: Map<String, JsonPrimitive> = emptyMap(),
)

private fun Map<String, JsonPrimitive>.toOptions(): List<FilterOption> = mapNotNull { (key, value) ->
    key.removePrefix("f").toIntOrNull()?.let { id -> FilterOption(id, value.content) }
}

fun FilterListDto.toDomain(): FilterOptions = FilterOptions(
    sections = sections.toOptions(),
    genres = categories.toOptions().sortedBy { it.label },
    countries = countries.toOptions().sortedBy { it.label },
    years = years.toOptions().sortedByDescending { it.id },
    voices = vo.toOptions().sortedBy { it.label },
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

/**
 * One entry of the `/api/v2/comments/{post_id}` array. Replies arrive twice:
 * flattened into the top-level array and nested under their parent. The nested
 * fields are deliberately undeclared — `childs` shifts type between an empty
 * array and a date-keyed object, and `children` only exists when non-empty —
 * so `ignoreUnknownKeys` skips both and threading is rebuilt from `parent_id`
 * (see `threadComments`).
 */
@Serializable
data class CommentDto(
    val id: Int = 0,
    @SerialName("parent_id") val parentId: Int = 0,
    val date: String = "",
    @SerialName("gast_name") val gastName: String = "",
    val text: String = "",
    /** Absolute URL for real avatars; a relative path for the default one. */
    val avatar: String = "",
)

fun CommentDto.toDomain(): Comment = Comment(
    id = id,
    parentId = parentId,
    date = date,
    author = Html.toPlainText(gastName),
    text = Html.toPlainText(text),
    avatarUrl = Avatar.urlOrNull(avatar),
)

/**
 * Reads the season → translation → episode tree.
 *
 * Held as a raw element because the field is polymorphic: an empty JSON array
 * for films and rights-blocked titles, an object for real series. Anything
 * that does not fit the expected shape is skipped rather than failing the
 * whole post — a single malformed episode should not cost the user the page.
 */
internal fun JsonElement?.toSeriesPlaylist(): SeriesPlaylist {
    val root = (this as? JsonObject) ?: return SeriesPlaylist.Empty
    val seasons = root.entries
        .sortedWith(compareBy(numericKey) { it.key })
        .mapNotNull { (seasonNumber, seasonNode) ->
            val translations = (seasonNode as? JsonObject)?.entries
                ?.mapNotNull { (translationName, episodesNode) ->
                    val episodes = (episodesNode as? JsonObject)?.entries
                        ?.sortedWith(compareBy(numericKey) { it.key })
                        ?.mapNotNull { (episodeNumber, episodeNode) ->
                            episodeNode.toEpisode(translationName, episodeNumber)
                        }
                        .orEmpty()
                    if (episodes.isEmpty()) null else SeriesTranslation(translationName, episodes)
                }
                .orEmpty()
            if (translations.isEmpty()) null else Season(seasonNumber, translations)
        }
    return SeriesPlaylist(seasons)
}

private val numericKey: Comparator<String> =
    compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })

private fun JsonElement.toEpisode(translation: String, number: String): Episode? {
    val node = this as? JsonObject ?: return null
    val link = node["link"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val qualities = runCatching {
        node["qualities"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }
    }.getOrNull().orEmpty()
    val source = StreamLink.fromTemplate(translation, link, qualities) ?: return null
    return Episode(number = number, source = source)
}

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
    rating = rating?.toIntOrNull() ?: 0,
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
    playlist = playerLinks?.playlist.toSeriesPlaylist(),
)
