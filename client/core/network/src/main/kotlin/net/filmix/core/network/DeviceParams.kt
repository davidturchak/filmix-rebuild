package net.filmix.core.network

import okhttp3.FormBody
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
 * Attaches the device identity and session token to every request, the way the
 * reference app does: query parameters on a GET, but the **form body** on a
 * POST. Its POST helper (`fq.w8UglNnmkNjtIbBL`) sends a bare URL and folds the
 * identity into the same `UrlEncodedFormEntity` as the endpoint's own fields —
 * and the backend reads them from there. With the token in the query instead,
 * add_watched answered 200 while attributing the watch to nobody, so history
 * stayed empty.
 */
class DeviceParamsInterceptor(
    private val params: DeviceParams,
    private val tokenProvider: TokenProvider,
) : Interceptor {

    private fun identity(): List<Pair<String, String>> = listOf(
        "user_dev_id" to params.deviceId,
        "user_dev_name" to params.deviceName,
        "user_dev_token" to tokenProvider.token(),
        "user_dev_vendor" to params.vendor,
        "user_dev_os" to params.osVersion,
        "user_dev_apk" to params.appVersion,
        "app_lang" to params.language,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        val decorated = if (request.method == "POST" && (body is FormBody || body == null || body.contentLength() == 0L)) {
            // Retrofit substitutes a zero-length body for a bodyless @POST;
            // both that and a real form body become one merged form body.
            val merged = FormBody.Builder().apply {
                if (body is FormBody) {
                    for (i in 0 until body.size) addEncoded(body.encodedName(i), body.encodedValue(i))
                }
                identity().forEach { (name, value) -> add(name, value) }
            }.build()
            request.newBuilder().method(request.method, merged).build()
        } else {
            val url = request.url.newBuilder().apply {
                identity().forEach { (name, value) -> addQueryParameter(name, value) }
            }.build()
            request.newBuilder().url(url).build()
        }
        return chain.proceed(decorated)
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
