package net.filmix.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _revision = MutableStateFlow(0)

    /**
     * Bumped whenever a call here changes what the user's lists contain.
     *
     * The toggles are reached from the detail screen, which knows nothing about
     * the library screen — and the library screen is activity-scoped, so it
     * kept whatever it had loaded: favouriting a film and going to Избранное
     * showed a list without it. Watching this is how it finds out.
     */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    suspend fun favourites(page: Int = 1): List<Post> = io {
        api.favourites(page = page).map { it.toDomain() }
    }

    suspend fun watchLater(page: Int = 1): List<Post> = io {
        api.deferred(page = page).map { it.toDomain() }
    }

    suspend fun history(page: Int = 1): List<Post> = io {
        api.history(page = page).map { it.toDomain() }
    }

    /** Clears every entry. Irreversible — callers should confirm first. */
    suspend fun clearHistory(): Boolean = io { api.historyClean().isSuccessful }

    suspend fun removeFromHistory(postId: Int): Boolean = io {
        api.historyRemove(postId).isSuccessful
    }

    /**
     * Both toggles are GET despite mutating server state — the reference app
     * routes them through its GET helper and POST is rejected.
     *
     * Returns the new state optimistically: the endpoint reports success but
     * not the resulting flag, so the caller flips its own copy.
     */
    suspend fun toggleFavourite(postId: Int): Boolean = io {
        api.toggleFavourite(postId).isSuccessful.also { if (it) _revision.value++ }
    }

    suspend fun toggleWatchLater(postId: Int): Boolean = io {
        api.toggleWatchLater(postId).isSuccessful.also { if (it) _revision.value++ }
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
