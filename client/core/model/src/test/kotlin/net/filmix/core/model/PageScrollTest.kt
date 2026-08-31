package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageScrollTest {

    @Test
    fun `an unmeasured window claims nothing`() {
        // Before layout the viewport is zero and maxValue is meaningless. A
        // press here must fall through, or the very first DOWN is swallowed.
        assertNull(PageScroll.target(0, Int.MAX_VALUE, viewport = 0, forward = true))
        assertNull(PageScroll.target(0, Int.MAX_VALUE, viewport = 0, forward = false))
    }

    @Test
    fun `text that fits claims nothing`() {
        assertNull(PageScroll.target(0, maxValue = 0, viewport = 300, forward = true))
        assertNull(PageScroll.target(0, maxValue = 0, viewport = 300, forward = false))
    }

    @Test
    fun `the press that finds the end is left to focus search`() {
        // This is what lets the cursor leave the reader for the close button.
        assertNull(PageScroll.target(900, maxValue = 900, viewport = 300, forward = true))
        assertNull(PageScroll.target(0, maxValue = 900, viewport = 300, forward = false))
    }

    @Test
    fun `a press with text still to reveal is claimed`() {
        assertNotNull(PageScroll.target(0, maxValue = 900, viewport = 300, forward = true))
        assertNotNull(PageScroll.target(900, maxValue = 900, viewport = 300, forward = false))
    }

    @Test
    fun `a page keeps the fold line on screen`() {
        val target = PageScroll.target(0, maxValue = 900, viewport = 300, forward = true)

        // Short of a full viewport, so the line the reader stopped on survives.
        assertEquals(255, target)
    }

    @Test
    fun `paging back is the mirror of paging forward`() {
        assertEquals(45, PageScroll.target(300, maxValue = 900, viewport = 300, forward = false))
    }

    @Test
    fun `the last page clamps instead of overshooting`() {
        assertEquals(900, PageScroll.target(800, maxValue = 900, viewport = 300, forward = true))
        assertEquals(0, PageScroll.target(40, maxValue = 900, viewport = 300, forward = false))
    }

    @Test
    fun `a window too small to page still moves`() {
        // 1 * 0.85 truncates to zero; a claimed press that scrolls nothing
        // would look like the remote had stopped working.
        val target = PageScroll.target(0, maxValue = 10, viewport = 1, forward = true)

        assertTrue("expected forward progress, got $target", (target ?: 0) > 0)
    }
}
