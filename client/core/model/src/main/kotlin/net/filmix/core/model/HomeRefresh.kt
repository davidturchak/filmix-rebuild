package net.filmix.core.model

/**
 * How the home screen's catalog rails are folded back into a screen that is
 * already showing them. When to do it is [RefreshPolicy]'s call.
 */
object HomeRefresh {

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
