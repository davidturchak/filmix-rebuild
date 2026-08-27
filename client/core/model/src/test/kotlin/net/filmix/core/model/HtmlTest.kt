package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTest {

    @Test
    fun `br tags become paragraph breaks`() {
        // Exactly the shape short_story arrives in.
        val input = "Первый абзац.<br /><br />Второй абзац."
        assertEquals("Первый абзац.\n\nВторой абзац.", Html.toPlainText(input))
    }

    @Test
    fun `br variants all match`() {
        assertEquals("a\nb", Html.toPlainText("a<br>b"))
        assertEquals("a\nb", Html.toPlainText("a<br/>b"))
        assertEquals("a\nb", Html.toPlainText("a<BR />b"))
    }

    @Test
    fun `named entities decode`() {
        assertEquals("Мама & папа", Html.toPlainText("Мама &amp; папа"))
        assertEquals("«Кино»", Html.toPlainText("&laquo;Кино&raquo;"))
        assertEquals("it's", Html.toPlainText("it&#039;s"))
    }

    @Test
    fun `numeric entities decode`() {
        assertEquals("A", Html.toPlainText("&#65;"))
    }

    @Test
    fun `stray tags are stripped`() {
        assertEquals("жирный текст", Html.toPlainText("<b>жирный</b> <i>текст</i>"))
    }

    @Test
    fun `runs of blank lines collapse`() {
        assertEquals("a\n\nb", Html.toPlainText("a<br /><br /><br /><br />b"))
    }

    @Test
    fun `null and empty are safe`() {
        assertEquals("", Html.toPlainText(null))
        assertEquals("", Html.toPlainText(""))
    }

    @Test
    fun `plain text passes through unchanged`() {
        assertEquals("обычный текст", Html.toPlainText("обычный текст"))
    }
}
