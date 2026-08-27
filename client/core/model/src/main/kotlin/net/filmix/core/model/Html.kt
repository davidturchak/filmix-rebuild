package net.filmix.core.model

/**
 * The API returns HTML fragments in text fields — `short_story` carries `<br />`
 * between paragraphs, and titles arrive with escaped entities. Rendering those
 * verbatim in Compose shows the raw markup, so they are flattened to plain text
 * here rather than at each call site.
 *
 * Deliberately small: this handles what the Filmix payloads actually contain,
 * not general HTML.
 */
object Html {

    private val BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val PARAGRAPH_END = Regex("""</p\s*>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val EXCESS_BLANK_LINES = Regex("""\n{3,}""")

    private val ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&#039;" to "'",
        "&#39;" to "'",
        "&laquo;" to "«",
        "&raquo;" to "»",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&hellip;" to "…",
    )

    fun toPlainText(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var text = BREAK.replace(input, "\n")
        text = PARAGRAPH_END.replace(text, "\n")
        text = TAG.replace(text, "")
        text = decodeEntities(text)
        return EXCESS_BLANK_LINES.replace(text, "\n\n")
            .lines().joinToString("\n") { it.trim() }
            .trim()
    }

    private fun decodeEntities(input: String): String {
        var text = input
        ENTITIES.forEach { (entity, replacement) -> text = text.replace(entity, replacement) }
        // Numeric entities the table does not name explicitly.
        return Regex("""&#(\d{1,6});""").replace(text) { match ->
            match.groupValues[1].toIntOrNull()
                ?.takeIf { it in 1..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
    }
}
