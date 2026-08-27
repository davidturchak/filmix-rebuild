package net.filmix.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.filmix.core.model.LastEpisode
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
)

@Serializable
data class LastEpisodeDto(
    val season: Int = 0,
    val episode: Int = 0,
)

fun PostDto.toDomain(): Post = Post(
    id = id,
    section = section,
    altName = altName,
    title = title,
    originalTitle = originalTitle,
    year = year,
    posterUrl = poster,
    quality = quality,
    date = date,
    duration = duration,
    shortStory = shortStory,
    kpRating = kpRating,
    imdbRating = imdbRating,
    countries = countries,
    categories = categories,
    actors = actors,
    directors = directors,
    favorited = favorited,
    watchLater = watchLater,
    lastEpisode = lastEpisode
        ?.takeIf { it.season > 0 || it.episode > 0 }
        ?.let { LastEpisode(it.season, it.episode) },
)
