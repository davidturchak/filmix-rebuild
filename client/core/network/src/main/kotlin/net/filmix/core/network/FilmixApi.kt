package net.filmix.core.network

import net.filmix.core.network.dto.FilterListDto
import net.filmix.core.network.dto.NotificationDto
import net.filmix.core.network.dto.PostDto
import net.filmix.core.network.dto.ServerListDto
import net.filmix.core.network.dto.TokenRequestDto
import net.filmix.core.network.dto.UserProfileDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
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
     * POST, unlike the read endpoints — GET returns 404 here. The fields must
     * travel as a **form-encoded body**: the reference app routes this through
     * its loopj POST helper (`fq.w8UglNnmkNjtIbBL`), whose params become a
     * `UrlEncodedFormEntity`. Sent as query params the server answers 200 and
     * silently records nothing, so history never fills — which is why this
     * cannot be `@Query` like everything else.
     *
     * `time` is the playback position in **seconds** and `quality` the height,
     * both mirrored from the reference app's reporter (`ag.LyO7ZE1i0MHQjQfQ`).
     */
    @FormUrlEncoded
    @POST("api/v2/add_watched")
    suspend fun addWatched(
        @Field("id") id: Int,
        @Field("translation") translation: String,
        @Field("season") season: String = "0",
        @Field("episode") episode: String = "0",
        @Field("time") time: Long = 0,
        @Field("quality") quality: Int = 0,
        @Field("add_watched") addWatched: Boolean = true,
    ): retrofit2.Response<Unit>

    @GET("api/v2/search")
    suspend fun search(
        @Query("story") story: String,
        @Query("page") page: Int = 1,
    ): List<PostDto>

    /**
     * Clears the whole watch history. GET despite being destructive — the
     * reference app routes it through its GET helper, unlike history/remove.
     */
    @GET("api/v2/history_clean")
    suspend fun historyClean(): retrofit2.Response<Unit>

    /** Removes one entry. POST, unlike history_clean — `id` in the form body. */
    @FormUrlEncoded
    @POST("api/v2/history/remove")
    suspend fun historyRemove(@Field("id") id: Int): retrofit2.Response<Unit>

    /** Available filter values: sections, categories, countries, years, vo. */
    @GET("api/v2/filter_list")
    suspend fun filterList(): FilterListDto

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
