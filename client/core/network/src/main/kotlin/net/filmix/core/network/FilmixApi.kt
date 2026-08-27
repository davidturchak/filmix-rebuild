package net.filmix.core.network

import net.filmix.core.network.dto.NotificationDto
import net.filmix.core.network.dto.PostDto
import net.filmix.core.network.dto.ServerListDto
import net.filmix.core.network.dto.TokenRequestDto
import net.filmix.core.network.dto.UserProfileDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Every endpoint is GET — verified against the live backend. `token_request`
 * in particular 404s on POST, despite reading like a mutation.
 *
 * The device identity params are added by [DeviceParamsInterceptor], so they
 * never appear in these signatures.
 */
interface FilmixApi {

    @GET("api/v2/token_request")
    suspend fun requestToken(): TokenRequestDto

    @GET("api/v2/user_profile")
    suspend fun userProfile(): UserProfileDto

    @GET("api/v2/catalog")
    suspend fun catalog(
        @Query("orderby") orderBy: String = "date",
        @Query("orderdir") orderDir: String = "desc",
        @Query("filter") filter: String? = null,
        @Query("page") page: Int = 1,
    ): List<PostDto>

    /** Full detail, including `short_story` and `player_links`. */
    @GET("api/v2/post/{id}")
    suspend fun post(@Path("id") id: Int): PostDto

    @GET("api/v2/top_views")
    suspend fun topViews(
        @Query("page") page: Int = 1,
        @Query("section") section: Int? = null,
    ): List<PostDto>

    @GET("api/v2/popular")
    suspend fun popular(
        @Query("page") page: Int = 1,
        @Query("section") section: Int? = null,
    ): List<PostDto>

    @GET("api/v2/history")
    suspend fun history(
        @Query("page") page: Int = 1,
        @Query("section") section: Int? = null,
    ): List<PostDto>

    /**
     * Reports playback progress; the backend keys history off this.
     *
     * POST, unlike the read endpoints — GET returns 404 here. Confirmed both
     * against the live server and in the reference app, which routes this
     * through its POST helper (`fq.w8UglNnmkNjtIbBL`).
     */
    @POST("api/v2/add_watched")
    suspend fun addWatched(
        @Query("id") id: Int,
        @Query("translation") translation: String,
        @Query("season") season: String = "",
        @Query("episode") episode: String = "",
        @Query("add_watched") addWatched: Boolean = true,
    ): retrofit2.Response<Unit>

    @GET("api/v2/search")
    suspend fun search(
        @Query("story") story: String,
        @Query("page") page: Int = 1,
    ): List<PostDto>

    /** Requires a paired account; returns [] otherwise. */
    @GET("api/v2/favourites")
    suspend fun favourites(
        @Query("orderby") orderBy: String = "date",
        @Query("orderdir") orderDir: String = "desc",
        @Query("page") page: Int = 1,
        @Query("section") section: Int? = null,
    ): List<PostDto>

    /** "Watch later". Requires a paired account; returns [] otherwise. */
    @GET("api/v2/deferred")
    suspend fun deferred(
        @Query("page") page: Int = 1,
        @Query("section") section: Int? = null,
    ): List<PostDto>

    /**
     * Toggles are GET despite mutating — the reference app routes both through
     * its GET helper, and POST is not accepted.
     */
    @GET("api/v2/toggle_fav/{id}")
    suspend fun toggleFavourite(@Path("id") id: Int): retrofit2.Response<Unit>

    @GET("api/v2/toggle_wl/{id}")
    suspend fun toggleWatchLater(@Path("id") id: Int): retrofit2.Response<Unit>

    /** `last_id` is required; omitting it returns HTTP 400. */
    @GET("api/v2/notifications/all")
    suspend fun notifications(@Query("last_id") lastId: Long = 0): List<NotificationDto>

    @POST("api/v2/notifications/readall")
    suspend fun markAllNotificationsRead(): retrofit2.Response<Unit>

    /** Type-ahead. Same entry shape as the catalog, minus the heavier fields. */
    @GET("api/v2/suggest")
    suspend fun suggest(@Query("word") word: String): List<PostDto>
}

/** Mirror discovery lives on a different host, so it takes an absolute URL. */
interface ServerDirectoryApi {
    @GET
    suspend fun servers(@Url url: String = SERVER_DIRECTORY_URL): ServerListDto

    companion object {
        const val SERVER_DIRECTORY_URL = "http://ap.gnft.pro/server.json"
    }
}

const val DEFAULT_BASE_URL = "http://filmixapp.cyou/"
