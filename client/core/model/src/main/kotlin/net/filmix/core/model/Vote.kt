package net.filmix.core.model

import kotlin.math.roundToInt

/**
 * An up or down vote on a title.
 *
 * [apiValue] is what `/api/v2/post/rate` expects in its `vote` field — the
 * literal characters the reference app sends, not a number.
 */
enum class Vote(val apiValue: String) {
    Up("+"),
    Down("-"),
    ;

    companion object {
        fun fromApiValue(value: String?): Vote? = entries.firstOrNull { it.apiValue == value }
    }
}

/**
 * The public tally on a title, plus whichever way this device voted.
 *
 * [own] cannot come from the API — no endpoint reports it, and the reference
 * app's thumbs are always plain — so it is remembered locally and carried here
 * only so the UI can fill the right thumb.
 */
data class VoteTally(
    val positive: Int,
    val negative: Int,
    val own: Vote? = null,
) {
    /** The site score shown on poster badges: up votes minus down votes. */
    val net: Int get() = positive - negative

    val total: Int get() = positive + negative

    /**
     * Share of up votes, or null when nobody has voted — the original app
     * prints "-" in that case rather than dividing by zero.
     */
    val percentPositive: Int?
        get() = if (total <= 0) null else (positive * 100f / total).roundToInt()

    /**
     * What the counts will most likely read once the vote lands, so the thumbs
     * respond immediately on a slow connection. The server answers with the
     * authoritative totals and those replace this, so a wrong guess about how
     * the backend treats a switched vote costs nothing.
     *
     * Voting the same way twice is a no-op: the API has no un-vote, and the
     * caller sends nothing in that case.
     */
    fun optimistic(tapped: Vote): VoteTally = when (own) {
        tapped -> this
        null -> plus(tapped, 1)
        else -> plus(tapped, 1).plus(own, -1)
    }.copy(own = tapped)

    private fun plus(vote: Vote, delta: Int): VoteTally = when (vote) {
        Vote.Up -> copy(positive = (positive + delta).coerceAtLeast(0))
        Vote.Down -> copy(negative = (negative + delta).coerceAtLeast(0))
    }
}
