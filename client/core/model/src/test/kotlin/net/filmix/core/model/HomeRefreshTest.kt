package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRefreshTest {

    private val order = listOf("continue", "new", "popular")
    private fun posts(vararg ids: Int) = ids.map { Post(id = it, title = "post $it") }

    private val stale = HomeRefresh.STALE_AFTER_MS
    private val retry = HomeRefresh.RETRY_AFTER_MS

    @Test
    fun `rails that never loaded are due`() {
        assertTrue(HomeRefresh.isDue(loadedAt = null, attemptedAt = null, now = 1_000_000))
    }

    @Test
    fun `a fresh load is trusted`() {
        assertFalse(HomeRefresh.isDue(loadedAt = 1_000, attemptedAt = 1_000, now = 1_000))
        assertFalse(HomeRefresh.isDue(loadedAt = 1_000, attemptedAt = 1_000, now = 1_000 + stale - 1))
    }

    @Test
    fun `a load past the threshold is due`() {
        assertTrue(HomeRefresh.isDue(loadedAt = 1_000, attemptedAt = 1_000, now = 1_000 + stale))
    }

    @Test
    fun `an attempt in flight is not raced by a second one`() {
        // Stale, but asked again a moment ago: the answer is still coming.
        assertFalse(HomeRefresh.isDue(loadedAt = 1_000, attemptedAt = 1_000 + stale, now = 1_000 + stale + 1))
        assertFalse(HomeRefresh.isDue(loadedAt = null, attemptedAt = 5_000, now = 5_000 + retry - 1))
    }

    @Test
    fun `a failed attempt leaves stale rails due once the retry gap has passed`() {
        // Only a load that landed moves loadedAt; a failure moved attemptedAt alone.
        val attempted = 1_000 + stale
        assertTrue(HomeRefresh.isDue(loadedAt = 1_000, attemptedAt = attempted, now = attempted + retry))
        assertTrue(HomeRefresh.isDue(loadedAt = null, attemptedAt = attempted, now = attempted + retry))
    }

    @Test
    fun `fresh rails replace the ones on screen in canonical order`() {
        val merged = HomeRefresh.mergeRails(
            order,
            current = mapOf("popular" to posts(1), "new" to posts(2)),
            fresh = mapOf("popular" to posts(3), "new" to posts(4)),
        )
        assertEquals(listOf("new" to posts(4), "popular" to posts(3)), merged)
    }

    @Test
    fun `a rail the fetch did not ask for is carried over`() {
        val merged = HomeRefresh.mergeRails(
            order,
            current = mapOf("continue" to posts(9), "new" to posts(2)),
            fresh = mapOf("new" to posts(4)),
        )
        assertEquals(listOf("continue" to posts(9), "new" to posts(4)), merged)
    }

    @Test
    fun `a rail whose fetch failed keeps its previous contents`() {
        val merged = HomeRefresh.mergeRails(
            order,
            current = mapOf("new" to posts(2), "popular" to posts(1)),
            fresh = mapOf("new" to emptyList(), "popular" to posts(3)),
        )
        assertEquals(listOf("new" to posts(2), "popular" to posts(3)), merged)
    }

    @Test
    fun `a rail empty both ways is not shown`() {
        val merged = HomeRefresh.mergeRails(
            order,
            current = mapOf("new" to posts(2)),
            fresh = mapOf("new" to posts(4), "popular" to emptyList()),
        )
        assertEquals(listOf("new" to posts(4)), merged)
    }

    @Test
    fun `a rail that first appears on a refresh is added in place`() {
        val merged = HomeRefresh.mergeRails(
            order,
            current = mapOf("popular" to posts(1)),
            fresh = mapOf("new" to posts(4), "popular" to posts(3)),
        )
        assertEquals(listOf("new" to posts(4), "popular" to posts(3)), merged)
    }
}
