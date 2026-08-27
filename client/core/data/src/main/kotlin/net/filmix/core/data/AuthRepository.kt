package net.filmix.core.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.filmix.core.network.FilmixApi
import net.filmix.core.network.dto.UserDataDto

/**
 * The device-code pairing flow, ported from `ProfileActivity` / `pk0.java`:
 *
 *  1. `GET /api/v2/token_request` → `{code, user_code, expire}`
 *  2. store `code` as the session token immediately — it is sent on every
 *     subsequent call, linked or not
 *  3. show `user_code` for the user to enter on the website
 *  4. poll `GET /api/v2/user_profile` until it returns a `user_data` object
 *
 * Until step 4 succeeds the profile endpoint returns a bare `{}`, which is the
 * only signal available that linking has not happened yet.
 */
class AuthRepository(
    private val api: FilmixApi,
    private val tokenStore: TokenStore,
) {

    val isPaired: Flow<Boolean> = tokenStore.isPaired

    suspend fun startPairing(): PairingCode {
        val response = api.requestToken()
        check(response.code.isNotEmpty()) { "token_request returned no code" }
        tokenStore.save(response.code)
        return PairingCode(
            userCode = response.userCode,
            expiresAt = response.expire,
        )
    }

    /** Null while unlinked. */
    suspend fun fetchProfile(): UserDataDto? = api.userProfile().userData

    /**
     * Emits [PairingState] until the device links or [timeoutMs] elapses.
     * Polling is the only option — the backend offers no push for this.
     */
    fun awaitPairing(
        pollIntervalMs: Long = 3_000,
        timeoutMs: Long = 5 * 60_000,
    ): Flow<PairingState> = flow {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val user = runCatching { fetchProfile() }.getOrNull()
            if (user != null) {
                emit(PairingState.Linked(user))
                return@flow
            }
            emit(PairingState.Waiting)
            delay(pollIntervalMs)
        }
        emit(PairingState.Expired)
    }

    suspend fun signOut() = tokenStore.clear()
}

data class PairingCode(val userCode: String, val expiresAt: Long)

sealed interface PairingState {
    data object Waiting : PairingState
    data class Linked(val user: UserDataDto) : PairingState
    data object Expired : PairingState
}
