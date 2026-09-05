package net.filmix.core.model

/**
 * When and how the home screen's catalog rails are refetched behind a screen
 * that is already showing them.
 *
 * The rails are loaded once when the screen is built, and the screen lives as
 * long as the process — which on the TV is days, because the launcher resumes
 * the backgrounded app rather than starting it. So Новинки stayed the same
 * Новинки until the system evicted the process. Reloading on every return
 * would be the other extreme: four requests each time the user glances at
 * Настройки and comes back. The rails go stale on the site's own timescale,
 * and [STALE_AFTER_MS] is about that.
 */
object HomeRefresh {

    /** How long a loaded set of rails is trusted before a resume refetches it. */
    const val STALE_AFTER_MS = 15L * 60 * 1000

    /**
     * Whether a resume at [now] should refetch rails loaded at [loadedAt].
     *
     * A null [loadedAt] means the first load has not finished: it is either in
     * flight or failed, and neither wants a second copy racing it.
     */
    fun isDue(loadedAt: Long?, now: Long): Boolean =
        loadedAt != null && now - loadedAt >= STALE_AFTER_MS

    /**
     * Folds a fresh fetch into the rails already on screen.
     *
     * Rails come out in [order]. A rail the fetch answered replaces the one on
     * screen; a rail the fetch did not ask for — «Продолжить просмотр», which
     * has its own triggers — is carried over as it is; and a rail the fetch
     * answered with nothing keeps its previous contents, because a failed
     * request collapses to an empty list and a stale rail beats a vanished one
     * on a refresh the user did not ask for. A rail that is empty both ways is
     * not shown at all, as on first load.
     */
    fun mergeRails(
        order: List<String>,
        current: Map<String, List<Post>>,
        fresh: Map<String, List<Post>>,
    ): List<Pair<String, List<Post>>> = order.mapNotNull { title ->
        val items = fresh[title]?.takeIf { it.isNotEmpty() } ?: current[title]
        items?.takeIf { it.isNotEmpty() }?.let { title to it }
    }
}
