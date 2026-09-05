package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRefreshTest {

    private val order = listOf("continue", "new", "popular")
    private fun posts(vararg ids: Int) = ids.map { Post(id = it, title = "post $it") }

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
