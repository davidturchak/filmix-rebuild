package net.filmix.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * The seven identity params the Filmix backend expects on *every* call.
 * Mirrors `defpackage/fq.java:O9juXBPWURVAM1Eg`, which appends the same set to
 * each request in the reference app.
 */
data class DeviceParams(
    /** `Settings.Secure.ANDROID_ID`. */
    val deviceId: String,
    /** `"<MANUFACTURER> <MODEL>"`, deduplicated when MODEL already starts with MANUFACTURER. */
    val deviceName: String,
    val vendor: String,
    val osVersion: String,
    val appVersion: String,
    val language: String,
)

/** Supplies the current session token, or "" when unpaired. */
fun interface TokenProvider {
    fun token(): String
}

/**
 * Appends the device identity and session token to every request as query
 * parameters. All Filmix endpoints — including the auth ones, which were
 * verified against the live backend — are GET.
 */
class DeviceParamsInterceptor(
    private val params: DeviceParams,
    private val tokenProvider: TokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.newBuilder()
            .addQueryParameter("user_dev_id", params.deviceId)
            .addQueryParameter("user_dev_name", params.deviceName)
            .addQueryParameter("user_dev_token", tokenProvider.token())
            .addQueryParameter("user_dev_vendor", params.vendor)
            .addQueryParameter("user_dev_os", params.osVersion)
            .addQueryParameter("user_dev_apk", params.appVersion)
            .addQueryParameter("app_lang", params.language)
            .build()
        return chain.proceed(request.newBuilder().url(url).build())
    }
}

/**
 * Content filters travel as bare header *presence*, not values — the reference
 * app sets them with empty strings.
 */
data class ContentFilters(
    val hideAnime: Boolean = false,
    val hideRussian: Boolean = false,
    val hideUkrainian: Boolean = false,
    val hideTurkish: Boolean = false,
    val hideKorean: Boolean = false,
    val hideIndian: Boolean = false,
) {
    internal fun headers(): List<String> = buildList {
        if (hideAnime) add("X-APP-NO-ANIME")
        if (hideRussian) add("X-APP-NO-RUSSIAN")
        if (hideUkrainian) add("X-APP-NO-UKRAINIAN")
        if (hideTurkish) add("X-APP-NO-TURKISH")
        if (hideKorean) add("X-APP-NO-KOREAN")
        if (hideIndian) add("X-APP-NO-INDIAN")
    }
}

class ContentFilterInterceptor(
    private val filters: () -> ContentFilters,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        filters().headers().forEach { builder.addHeader(it, "") }
        return chain.proceed(builder.build())
    }
}
