package net.filmix.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device is linked to an account, shared by every screen that cares.
 *
 * Linking cannot be observed from the token: `token_request` issues one up
 * front, and the account is attached later, on the website — only
 * `user_profile` reveals that it happened. So the token flow never emits at the
 * moment that matters, and a screen that asked once when it was built stayed
 * unpaired for the rest of the process. Library and history watch this instead,
 * which is what makes entering the code populate them.
 *
 * Null means nobody has asked yet, which is distinct from a known "not linked".
 */
class SessionState {

    private val _linked = MutableStateFlow<Boolean?>(null)
    val linked: StateFlow<Boolean?> = _linked.asStateFlow()

    fun set(linked: Boolean) {
        _linked.value = linked
    }
}
