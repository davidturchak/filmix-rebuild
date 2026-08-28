package net.filmix.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire format of the mutating POSTs against the reference app's
 * behaviour. Its POST helper sends a **bare URL** and folds the endpoint's
 * fields *and* the seven identity params into one form body; the backend reads
 * both from there. Sent as query params instead, add_watched answers 200 and
 * records nothing — history silently never fills. That shipped once, twice:
 * first the fields were @Query, then the identity was. This test fails if
 * either regresses.
 */
class AddWatchedContractTest {

    private val server = MockWebServer()

    private val client = NetworkFactory.okHttp(
        deviceParams = DeviceParams(
            deviceId = "cafe0123deadbeef",
            deviceName = "Test Device",
            vendor = "Test",
            osVersion = "14",
            appVersion = "0.5.4",
            language = "ru",
        ),
        tokenProvider = { "token123" },
    )

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `add_watched sends fields and identity as one form body, bare url`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val api = NetworkFactory.filmixApi(client, server.url("/").toString())

        api.addWatched(
            id = 186499,
            translation = "MVO",
            season = "1",
            episode = "4",
            time = 300,
            quality = 1080,
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v2/add_watched", recorded.requestUrl?.encodedPath)
        // Bare URL, like the reference app's HttpPost(URI.create(url)).
        assertTrue(recorded.requestUrl?.query.isNullOrEmpty())
        assertEquals(
            "application/x-www-form-urlencoded",
            recorded.getHeader("Content-Type")?.substringBefore(';'),
        )
        val body = recorded.body.readUtf8()
        assertEquals(
            "id=186499&translation=MVO&season=1&episode=4&time=300&quality=1080&add_watched=true" +
                "&user_dev_id=cafe0123deadbeef&user_dev_name=Test%20Device&user_dev_token=token123" +
                "&user_dev_vendor=Test&user_dev_os=14&user_dev_apk=0.5.4&app_lang=ru",
            body,
        )
        assertFalse(body.contains("%5B")) // a raw "[...]" label would arrive encoded
    }

    @Test
    fun `history remove posts its id and identity in the body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val api = NetworkFactory.filmixApi(client, server.url("/").toString())

        api.historyRemove(186499)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.requestUrl?.query.isNullOrEmpty())
        val body = recorded.body.readUtf8()
        assertTrue(body.startsWith("id=186499&user_dev_id="))
        assertTrue(body.contains("user_dev_token=token123"))
    }

    @Test
    fun `get requests keep identity in the query`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val api = NetworkFactory.filmixApi(client, server.url("/").toString())

        api.history()

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("token123", recorded.requestUrl?.queryParameter("user_dev_token"))
    }
}
