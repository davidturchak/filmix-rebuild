package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {

    private fun version(
        name: String = "0.2.0",
        code: Int = 15,
        sha: String = "1fa5672",
        dirty: Boolean = false,
        debug: Boolean = false,
    ) = AppVersion(name, code, sha, dirty, debug)

    @Test
    fun `short is name and code`() {
        assertEquals("0.2.0 (15)", version().short)
    }

    @Test
    fun `full adds the commit`() {
        assertEquals("0.2.0 (15) · 1fa5672", version().full)
    }

    @Test
    fun `dirty tree is starred`() {
        assertEquals("0.2.0 (15) · 1fa5672*", version(dirty = true).full)
    }

    @Test
    fun `debug builds say so`() {
        assertEquals("0.2.0 (15) · 1fa5672 · debug", version(debug = true).full)
    }

    @Test
    fun `an unknown sha is omitted rather than printed`() {
        assertEquals("0.2.0 (15)", version(sha = AppVersion.UNKNOWN).full)
        assertEquals("0.2.0 (15)", version(sha = "").full)
    }
}
