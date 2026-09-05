package net.filmix.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {

    private val stale = RefreshPolicy.STALE_AFTER_MS
    private val retry = RefreshPolicy.RETRY_AFTER_MS

    @Test
    fun `content that never loaded is due`() {
        assertTrue(RefreshPolicy.isDue(loadedAt = null, attemptedAt = null, now = 1_000_000))
    }

    @Test
    fun `a fresh load is trusted`() {
        assertFalse(RefreshPolicy.isDue(loadedAt = 1_000, attemptedAt = 1_000, now = 1_000))
        assertFalse(RefreshPolicy.isDue(loadedAt = 1_000, attemptedAt = 1_000, now = 1_000 + stale - 1))
    }

    @Test
    fun `a load past the threshold is due`() {
        assertTrue(RefreshPolicy.isDue(loadedAt = 1_000, attemptedAt = 1_000, now = 1_000 + stale))
    }

    @Test
    fun `an attempt in flight is not raced by a second one`() {
        // Stale, but asked again a moment ago: the answer is still coming.
        assertFalse(RefreshPolicy.isDue(loadedAt = 1_000, attemptedAt = 1_000 + stale, now = 1_000 + stale + 1))
        assertFalse(RefreshPolicy.isDue(loadedAt = null, attemptedAt = 5_000, now = 5_000 + retry - 1))
    }

    @Test
    fun `a failed attempt leaves stale content due once the retry gap has passed`() {
        // Only a load that landed moves loadedAt; a failure moved attemptedAt alone.
        val attempted = 1_000 + stale
        assertTrue(RefreshPolicy.isDue(loadedAt = 1_000, attemptedAt = attempted, now = attempted + retry))
        assertTrue(RefreshPolicy.isDue(loadedAt = null, attemptedAt = attempted, now = attempted + retry))
    }
}
