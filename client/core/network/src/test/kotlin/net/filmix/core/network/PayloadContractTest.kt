package net.filmix.core.network

import net.filmix.core.network.dto.PostDto
import net.filmix.core.network.dto.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses payloads captured verbatim from the live API.
 *
 * These exist because the API's shapes are not what they look like: ratings
 * arrive as bare numbers, `last_episode` values are strings that may be ranges,
 * and `playlist` switches between an array and an object. Each of those has
 * already broken a release once; fixtures keep them broken loudly, in a test,
 * rather than silently at runtime.
 */
class PayloadContractTest {

    private val json = NetworkFactory.json

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `catalog page parses`() {
        val posts = json.decodeFromString<List<PostDto>>(fixture("catalog.json"))
        assertEquals(50, posts.size)
        assertTrue(posts.all { it.id > 0 })
        assertTrue(posts.all { it.title.isNotEmpty() })
    }

    @Test
    fun `episode ranges survive as strings`() {
        val posts = json.decodeFromString<List<PostDto>>(fixture("catalog.json"))
        val withEpisodes = posts.mapNotNull { it.toDomain().lastEpisode }
        assertTrue("expected series entries in the fixture", withEpisodes.isNotEmpty())
        // "1-4" and "13-14" appear in real responses and are not integers.
        assertTrue(withEpisodes.any { it.episode.contains('-') })
    }

    @Test
    fun `movie detail parses including numeric ratings`() {
        val post = json.decodeFromString<PostDto>(fixture("post_movie.json"))
        assertEquals(186499, post.id)
        // kp_rating/imdb_rating come back as bare numbers, not quoted strings.
        assertNotNull(post.kpRating)
        assertEquals("WEB-DLRip 2160", post.rip)
        assertTrue(post.relates.isNotEmpty())
        assertTrue(post.foundActors.isNotEmpty())
    }

    @Test
    fun `series detail parses with fractional ratings`() {
        val post = json.decodeFromString<PostDto>(fixture("post_series.json"))
        assertEquals(8975, post.id)
        assertEquals("8.195", post.kpRating)
        assertEquals("8.5", post.imdbRating)
    }

    @Test
    fun `playlist tolerates both the array and object shapes`() {
        // Both fixtures carry `"playlist": []`; the object form must not throw
        // either, which is what holding it as a raw element guarantees.
        val movie = json.decodeFromString<PostDto>(fixture("post_movie.json"))
        val series = json.decodeFromString<PostDto>(fixture("post_series.json"))
        assertNotNull(movie.playerLinks)
        assertNotNull(series.playerLinks)

        val objectShaped = """
            {"id":1,"title":"x","player_links":{"movie":[],"playlist":{"1":{"1":{"link":"u"}}}}}
        """.trimIndent()
        val parsed = json.decodeFromString<PostDto>(objectShaped)
        assertNotNull(parsed.playerLinks?.playlist)
    }

    @Test
    fun `movie links expose translations and quality templates`() {
        val post = json.decodeFromString<PostDto>(fixture("post_movie.json"))
        val links = checkNotNull(post.playerLinks).movie
        assertEquals(3, links.size)
        assertTrue(links.all { it.translation.isNotEmpty() })
        assertTrue(links.all { it.link.contains(".mp4") })
    }
}
