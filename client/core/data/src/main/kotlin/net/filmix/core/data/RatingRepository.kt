package net.filmix.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.filmix.core.model.Vote
import net.filmix.core.model.VoteTally
import net.filmix.core.network.FilmixApi

/**
 * Up/down votes on a title.
 *
 * Separate from [LibraryRepository], whose endpoints all move a title in or
 * out of one of the user's lists — a vote changes the title's public tally
 * instead — and it keeps the network call next to [VoteStore], the only record
 * that this device voted at all.
 */
class RatingRepository(
    private val api: FilmixApi,
    private val store: VoteStore,
) {

    /** Whichever way this device last voted, or null if it never did. */
    suspend fun ownVote(postId: Int): Vote? = io { store.vote(postId) }

    /**
     * Casts [vote] and returns the totals the server reports back.
     *
     * Null when the server refuses — it answers 200 with a body that simply
     * omits the counts, so the absence of them is the only rejection signal
     * there is. Callers keep the counts they had rather than showing zeroes.
     */
    suspend fun rate(postId: Int, vote: Vote): VoteTally? = io {
        val reply = api.ratePost(id = postId, vote = vote.apiValue)
        val positive = reply.ratePositive
        val negative = reply.rateNegative
        if (positive == null || negative == null) return@io null
        store.setVote(postId, vote)
        VoteTally(positive = positive, negative = negative, own = vote)
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        runCatching { block() }.onFailure { Log.w(TAG, "rating call failed", it) }.getOrThrow()
    }

    private companion object {
        const val TAG = "RatingRepository"
    }
}
