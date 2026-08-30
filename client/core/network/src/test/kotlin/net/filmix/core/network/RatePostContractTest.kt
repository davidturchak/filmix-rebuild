package net.filmix.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the wire format of a cast vote.
 *
 * The `+` is the whole reason this test exists: a form body is decoded with
 * plus-as-space, so a raw `+` arrives at the backend as `" "` and the vote
 * reads as neither direction. It has to travel as `%2B`.
 */
class RatePostContractTest {

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
    fun `an up vote posts id and an encoded plus with the identity`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"rate_p":46,"rate_n":8}"""))
        val api = NetworkFactory.filmixApi(client, server.url("/").toString())

        val reply = api.ratePost(id = 186499, vote = "+")

        assertEquals(46, reply.ratePositive)
        assertEquals(8, reply.rateNegative)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v2/post/rate", recorded.requestUrl?.encodedPath)
        // Bare URL, like the reference app's POST helper.
        assertTrue(recorded.requestUrl?.query.isNullOrEmpty())
        assertEquals(
            "application/x-www-form-urlencoded",
            recorded.getHeader("Content-Type")?.substringBefore(';'),
        )
        assertEquals(
            "id=186499&vote=%2B" +
                "&user_dev_id=cafe0123deadbeef&user_dev_name=Test%20Device&user_dev_token=token123" +
                "&user_dev_vendor=Test&user_dev_os=14&user_dev_apk=0.5.4&app_lang=ru",
            recorded.body.readUtf8(),
        )
    }

    @Test
    fun `a down vote posts a bare minus`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"rate_p":46,"rate_n":9}"""))
        val api = NetworkFactory.filmixApi(client, server.url("/").toString())

        api.ratePost(id = 186499, vote = "-")

        assertTrue(server.takeRequest().body.readUtf8().startsWith("id=186499&vote=-&user_dev_id="))
    }

    /**
     * A refusal is a 200 whose body simply lacks the counts — the reference app
     * checks for exactly that before touching its labels. It must not decode
     * into a believable zero tally, which would wipe the counts on screen.
     */
    @Test
    fun `a refused vote leaves both counts null`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"message":null}"""))
        val api = NetworkFactory.filmixApi(client, server.url("/").toString())

        val reply = api.ratePost(id = 186499, vote = "+")

        assertNull(reply.ratePositive)
        assertNull(reply.rateNegative)
    }
}
