package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comments payload is a flattened array: replies sit alongside the
 * comments they answer, tied only by `parent_id`, and chains can nest
 * arbitrarily deep. threadComments() has to rebuild that into the one-indent
 * display the original app used without losing or duplicating anyone.
 */
class CommentTest {

    private fun comment(id: Int, parentId: Int = 0) = Comment(
        id = id,
        parentId = parentId,
        date = "19 дек 2018",
        author = "Пользователь$id",
        text = "Текст $id",
        avatarUrl = null,
    )

    @Test
    fun `flattened replies attach under their root`() {
        val threads = threadComments(
            listOf(comment(30, parentId = 10), comment(20), comment(10)),
        )
        assertEquals(listOf(20, 10), threads.map { it.root.id })
        assertEquals(listOf(30), threads.first { it.root.id == 10 }.replies.map { it.id })
    }

    @Test
    fun `a multi-level chain collapses to one reply level under its root`() {
        // 3 answers 2, which answers 1: both land as direct replies of 1.
        val threads = threadComments(
            listOf(comment(3, parentId = 2), comment(2, parentId = 1), comment(1)),
        )
        assertEquals(1, threads.size)
        assertEquals(listOf(2, 3), threads.single().replies.map { it.id })
    }

    @Test
    fun `roots keep array order and replies sort ascending by id`() {
        // Served newest-first; replies read chronologically within a thread.
        val threads = threadComments(
            listOf(
                comment(50),
                comment(40, parentId = 20),
                comment(30, parentId = 20),
                comment(20),
            ),
        )
        assertEquals(listOf(50, 20), threads.map { it.root.id })
        assertEquals(listOf(30, 40), threads.last().replies.map { it.id })
    }

    @Test
    fun `empty payload threads to nothing`() {
        assertTrue(threadComments(emptyList()).isEmpty())
    }

    @Test
    fun `an orphan reply surfaces as a root instead of disappearing`() {
        val threads = threadComments(
            listOf(comment(7, parentId = 999), comment(5)),
        )
        assertEquals(listOf(7, 5), threads.map { it.root.id })
    }

    @Test
    fun `thread size counts the root and its replies`() {
        val threads = threadComments(
            listOf(comment(2, parentId = 1), comment(1)),
        )
        assertEquals(2, threads.single().size)
    }
}
