package net.filmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.filmix.core.network.TokenProvider

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "filmix_auth")

/**
 * Persists the session token.
 *
 * OkHttp interceptors are synchronous, so the current value is mirrored into a
 * volatile field that [asTokenProvider] reads. DataStore stays the source of
 * truth; the mirror only exists to bridge the sync/async boundary.
 */
class TokenStore(private val context: Context) {

    @Volatile
    private var cached: String = ""

    @Volatile
    private var primed: Boolean = false

    val token: Flow<String> = context.authDataStore.data.map { prefs ->
        (prefs[KEY_TOKEN] ?: "").also {
            cached = it
            primed = true
        }
    }

    /** True once the device has been linked and the profile call returns user data. */
    val isPaired: Flow<Boolean> = token.map(String::isNotEmpty)

    suspend fun save(token: String) {
        cached = token
        primed = true
        context.authDataStore.edit { it[KEY_TOKEN] = token }
    }

    /** Logout is local-only in the reference app; there is no server call. */
    suspend fun clear() {
        cached = ""
        context.authDataStore.edit { it.remove(KEY_TOKEN) }
    }

    /** Warms [cached] so the very first request carries a token after process start. */
    suspend fun prime() {
        cached = context.authDataStore.data.first()[KEY_TOKEN] ?: ""
        primed = true
    }

    /**
     * Reads the stored token, loading it on first use.
     *
     * OkHttp interceptors are synchronous, so the cache cannot be filled
     * lazily by a collector — on a cold start the first requests would go out
     * with an empty token and the account-scoped endpoints would answer `[]`,
     * silently hiding history and favourites until some other screen happened
     * to read the flow. The one-time blocking read runs on OkHttp's own
     * dispatcher thread, never the main thread.
     */
    fun asTokenProvider(): TokenProvider = TokenProvider {
        if (!primed) runBlocking { prime() }
        cached
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("auth_token")
    }
}
