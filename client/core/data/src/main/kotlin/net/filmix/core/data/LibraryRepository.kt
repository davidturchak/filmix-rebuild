package net.filmix.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.filmix.core.model.Post
import net.filmix.core.network.FilmixApi
import net.filmix.core.network.dto.toDomain

/**
 * The user's own lists. Every endpoint here is account-scoped: unpaired
 * clients get an empty array rather than an error, so an empty library is
 * indistinguishable from "not signed in" at this layer — the UI decides which
 * message to show using the pairing state.
 */
class LibraryRepository(
    private val api: FilmixApi,
    private val tokenStore: TokenStore,
) {

    suspend fun favourites(page: Int = 1): List<Post> = io {
        api.favourites(page = page).map { it.toDomain() }
    }

    suspend fun watchLater(page: Int = 1): List<Post> = io {
        api.deferred(page = page).map { it.toDomain() }
    }

    /**
     * Both toggles are GET despite mutating server state — the reference app
     * routes them through its GET helper and POST is rejected.
     *
     * Returns the new state optimistically: the endpoint reports success but
     * not the resulting flag, so the caller flips its own copy.
     */
    suspend fun toggleFavourite(postId: Int): Boolean = io {
        api.toggleFavourite(postId).isSuccessful
    }

    suspend fun toggleWatchLater(postId: Int): Boolean = io {
        api.toggleWatchLater(postId).isSuccessful
    }

    /**
     * Holding a token is not the same as being linked: `token_request` issues
     * one immediately, but the account is only attached once the user enters
     * the code on the website. Until then `user_profile` returns `{}` and the
     * list endpoints return `[]` — indistinguishable from a genuinely empty
     * library unless we ask the profile endpoint.
     */
    suspend fun isSignedIn(): Boolean = io {
        tokenStore.token.first().isNotEmpty() && api.userProfile().userData != null
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        runCatching { block() }.onFailure { Log.w(TAG, "library call failed", it) }.getOrThrow()
    }

    private companion object {
        const val TAG = "LibraryRepository"
    }
}
