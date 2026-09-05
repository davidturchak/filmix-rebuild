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
     * How long after an attempt — landed or not — before a resume may start
     * another. Long enough to cover a request in flight, so a resume that
     * arrives mid-fetch does not race it with a second copy; short enough
     * that a fetch which failed because Wi-Fi was still reassociating after
     * standby is retried on the next glance, not in fifteen minutes.
     */
    const val RETRY_AFTER_MS = 30L * 1000

    /**
     * Whether a resume at [now] should refetch rails that last loaded at
     * [loadedAt] — null when they never have — given that the last attempt to
     * load them was at [attemptedAt], null when there has been none.
     *
     * Only a load that came back with something moves [loadedAt]; a failure
     * moves [attemptedAt] alone, so stale rails stay due and are retried as
     * soon as the retry gap allows. Rails that never loaded are due outright:
     * the screen may be showing «Продолжить просмотр» on its own, with
     * nothing to say the catalog was ever asked.
     */
    fun isDue(loadedAt: Long?, attemptedAt: Long?, now: Long): Boolean {
        val stale = loadedAt == null || now - loadedAt >= STALE_AFTER_MS
        val attemptedRecently = attemptedAt != null && now - attemptedAt < RETRY_AFTER_MS
        return stale && !attemptedRecently
    }

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
