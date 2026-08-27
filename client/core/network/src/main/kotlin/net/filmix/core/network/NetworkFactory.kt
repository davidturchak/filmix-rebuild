package net.filmix.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkFactory {

    /**
     * The API is generous with fields we do not model and occasionally omits
     * ones we do, so both leniencies are required rather than optional.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    fun okHttp(
        deviceParams: DeviceParams,
        tokenProvider: TokenProvider,
        filters: () -> ContentFilters = { ContentFilters() },
        debugLogging: Boolean = false,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(DeviceParamsInterceptor(deviceParams, tokenProvider))
        .addInterceptor(ContentFilterInterceptor(filters))
        .apply {
            if (debugLogging) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    fun retrofit(client: OkHttpClient, baseUrl: String = DEFAULT_BASE_URL): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun filmixApi(client: OkHttpClient, baseUrl: String = DEFAULT_BASE_URL): FilmixApi =
        retrofit(client, baseUrl).create(FilmixApi::class.java)

    fun serverDirectoryApi(client: OkHttpClient): ServerDirectoryApi =
        retrofit(client).create(ServerDirectoryApi::class.java)
}
