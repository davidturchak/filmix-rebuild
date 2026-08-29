package net.filmix.core.model

/**
 * Ports the stream-link handling from the reference app
 * (`defpackage/r21.java`, `oUwea6YHUgxV0pYe`).
 *
 * A single API link encodes every available quality as a bracketed CSV:
 *
 *     .../Batman.Knightfall.2026.MVO.ru.WEBDL.1080pp_[,1440,1080,720,480,].mp4
 *
 * The bracket group is replaced with `%s` to yield a template, and the listed
 * heights become the quality menu. Note the empty entries either side of the
 * list — the upstream parser skips them, and so must this one; live API
 * responses really do contain `[,1440,...` and `[2160,,1080,...`.
 */
object StreamLink {

    private val QUALITY_BRACKET = Regex("""\[[0-9,p]+]""")

    // Precompiled: resumeKey runs per episode inside the watched-state scans.
    private val QUALITY_SUFFIX = Regex("""_\d+\.mp4""")

    /** Returns null when the link carries no quality bracket. */
    fun parse(rawTranslation: String, link: String): VideoSource? {
        val match = QUALITY_BRACKET.find(link) ?: return null
        val qualities = match.value
            .trim('[', ']')
            .split(',')
            .mapNotNull { it.removeSuffix("p").trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
        if (qualities.isEmpty()) return null
        return VideoSource(
            rawTranslation = rawTranslation,
            templateUrl = QUALITY_BRACKET.replace(link, "%s"),
            qualities = qualities.distinct().sortedDescending(),
        )
    }

    /**
     * Builds a source from a series episode, which needs no bracket parsing:
     * the API already supplies a `%s` template and an explicit quality list.
     * Returns null if either is missing or the template has no placeholder.
     */
    fun fromTemplate(
        translation: String,
        link: String,
        qualities: List<Int>,
    ): VideoSource? {
        if (link.isEmpty() || !link.contains("%s") || qualities.isEmpty()) return null
        return VideoSource(
            rawTranslation = translation,
            templateUrl = link,
            qualities = qualities.distinct().sortedDescending(),
        )
    }

    /**
     * Picks [preferred] when the source offers it, else the highest available —
     * matching the reference app's `Collections.max` fallback.
     */
    fun selectQuality(source: VideoSource, preferred: Int?): Int? =
        preferred?.takeIf { it in source.qualities } ?: source.bestQuality

    /** Display label for a height. 1440 and 2160 get the names the app shows. */
    fun label(quality: Int): String = when (quality) {
        2160 -> "4K"
        1440 -> "1080 Ultra+"
        else -> "${quality}p"
    }

    /**
     * Normalises a played URL into a stable key for resume positions, so the
     * saved position survives a change of quality — and a refetch of the post.
     *
     * Stream URLs are signed: `https://nl205.cdnsqu.com/s/<token>/<folder>/<file>`,
     * and the token rotates within minutes (two fetches of the same post ten
     * minutes apart returned different tokens). Keying by the full URL therefore
     * orphaned every stored position as soon as the post was fetched again —
     * which is also why resume only appeared to work within a single session.
     * Only the trailing `<folder>/<file>` is stable, so that is the key; it also
     * survives a change of CDN host. The quality substitution mirrors the
     * reference app's `FullMovie.java:787`.
     */
    fun resumeKey(url: String): String {
        val normalized = url
            .substringBefore('?')
            .replace(QUALITY_SUFFIX, "_%s.mp4")
        val path = normalized.substringAfter("://", normalized).substringAfter('/', "")
        val segments = path.split('/').filter { it.isNotEmpty() }
        // Strip the signed `/s/<token>/` prefix outright: takeLast(2) alone
        // would bake the rotating token into the key of a file sitting
        // directly under it (`/s/<token>/file.mp4`), quietly reintroducing
        // the per-refetch orphaning for that shape.
        val stable = if (segments.size > 2 && segments[0] == "s") segments.drop(2) else segments
        return if (stable.isEmpty()) normalized else stable.takeLast(2).joinToString("/")
    }
}

/**
 * The API hands back one opaque string per source, e.g.
 * `"Дубляж [4K, SDR, ru, HDRezka]"`. The original app rendered it verbatim;
 * splitting it lets the UI show real chips instead.
 */
data class ParsedTranslation(
    val voice: String,
    val tags: List<String>,
) {
    companion object {
        private val BRACKET = Regex("""^(.*?)\s*\[(.*)]\s*$""")

        fun from(raw: String): ParsedTranslation {
            val match = BRACKET.find(raw.trim())
                ?: return ParsedTranslation(raw.trim(), emptyList())
            val voice = match.groupValues[1].trim()
            val tags = match.groupValues[2]
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
            return ParsedTranslation(voice.ifEmpty { raw.trim() }, tags)
        }
    }
}
