package net.filmix.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.filmix.core.data.AuthRepository
import net.filmix.core.data.PairingState

class ProfileViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        // A token may already be stored from a previous run; if it is linked the
        // profile call returns immediately and we skip pairing entirely.
        viewModelScope.launch {
            runCatching { auth.fetchProfile() }.getOrNull()?.let { _state.value = it.toSignedIn() }
        }
    }

    fun startPairing() {
        pollJob?.cancel()
        _state.value = ProfileUiState.Requesting
        pollJob = viewModelScope.launch {
            val code = runCatching { auth.startPairing() }.getOrElse { error ->
                _state.value = ProfileUiState.Failed(error.message ?: error::class.simpleName.orEmpty())
                return@launch
            }
            _state.value = ProfileUiState.AwaitingLink(code.userCode)
            auth.awaitPairing().collect { pairing ->
                _state.value = when (pairing) {
                    is PairingState.Linked -> pairing.user.toSignedIn()
                    else -> ProfileUiState.AwaitingLink(code.userCode, pairing)
                }
            }
        }
    }

    fun signOut() {
        pollJob?.cancel()
        viewModelScope.launch {
            auth.signOut()
            _state.value = ProfileUiState.Idle
        }
    }
}

private fun net.filmix.core.network.dto.UserDataDto.toSignedIn() = ProfileUiState.SignedIn(
    displayName = displayName.ifEmpty { login },
    isPro = isPro || isProPlus,
    proUntil = proDate,
)
