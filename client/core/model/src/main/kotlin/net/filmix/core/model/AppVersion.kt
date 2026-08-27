package net.filmix.core.model

/**
 * Build identity, surfaced in the UI.
 *
 * A version name alone is not enough to answer "is this the build I just
 * installed?", which is the question that actually gets asked — several builds
 * share a version name during development. The commit and dirty flag make each
 * build distinguishable.
 */
data class AppVersion(
    val name: String,
    val code: Int,
    val gitSha: String,
    val gitDirty: Boolean,
    val debug: Boolean,
) {
    /** e.g. `0.2.0 (15)` — what a user would quote in a bug report. */
    val short: String get() = "$name ($code)"

    /** e.g. `0.2.0 (15) · 1fa5672* · debug` — enough to identify one build. */
    val full: String
        get() = buildString {
            append(short)
            if (gitSha.isNotEmpty() && gitSha != UNKNOWN) {
                append(" · ").append(gitSha)
                if (gitDirty) append('*')
            }
            if (debug) append(" · debug")
        }

    companion object {
        const val UNKNOWN = "unknown"
    }
}
