package net.filmix.core.model

/**
 * When a screen that loaded its content once should fetch it again on resume.
 *
 * Screens are activity-scoped with no backstack, and the process lives as long
 * as the launcher lets it — on the TV that is days, because it resumes the
 * backgrounded app rather than starting it. So a list loaded once stayed that
 * list until the system evicted the process. Reloading on every return would
 * be the other extreme: a burst of requests each time the user glances at
 * Настройки and comes back. Catalog content goes stale on the site's own
 * timescale, and [STALE_AFTER_MS] is about that. The home rails and the
 * Каталог grid share this rule.
 */
object RefreshPolicy {

    /** How long a loaded result is trusted before a resume refetches it. */
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
     * Whether a resume at [now] should refetch content that last loaded at
     * [loadedAt] — null when it never has — given that the last attempt to
     * load it was at [attemptedAt], null when there has been none.
     *
     * Only a load that came back with something moves [loadedAt]; a failure
     * moves [attemptedAt] alone, so stale content stays due and is retried as
     * soon as the retry gap allows. Content that never loaded is due outright:
     * the home screen may be showing «Продолжить просмотр» on its own, with
     * nothing to say the catalog was ever asked.
     */
    fun isDue(loadedAt: Long?, attemptedAt: Long?, now: Long): Boolean {
        val stale = loadedAt == null || now - loadedAt >= STALE_AFTER_MS
        val attemptedRecently = attemptedAt != null && now - attemptedAt < RETRY_AFTER_MS
        return stale && !attemptedRecently
    }
}
