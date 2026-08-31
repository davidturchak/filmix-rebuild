package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarTest {

    @Test
    fun `keeps an uploaded picture`() {
        val url = "http://thumbs.filmixapp.cyou/fotos/foto_385722.png"
        assertEquals(url, Avatar.urlOrNull(url))
    }

    @Test
    fun `drops the site's placeholder path`() {
        assertNull(Avatar.urlOrNull("/templates/Filmix/dleimages/noavatar.png"))
    }

    @Test
    fun `drops an absent field`() {
        assertNull(Avatar.urlOrNull(""))
        assertNull(Avatar.urlOrNull(null))
    }
}
