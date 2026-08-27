package net.filmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    val token: Flow<String> = context.authDataStore.data.map { prefs ->
        (prefs[KEY_TOKEN] ?: "").also { cached = it }
    }

    /** True once the device has been linked and the profile call returns user data. */
    val isPaired: Flow<Boolean> = token.map(String::isNotEmpty)

    suspend fun save(token: String) {
        cached = token
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
    }

    fun asTokenProvider(): TokenProvider = TokenProvider { cached }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("auth_token")
    }
}
