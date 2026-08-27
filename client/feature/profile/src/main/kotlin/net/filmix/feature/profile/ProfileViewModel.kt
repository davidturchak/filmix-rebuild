package net.filmix.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.filmix.core.data.AuthRepository
import net.filmix.core.data.SettingsStore
import net.filmix.core.data.DownloadProgress
import net.filmix.core.data.UpdateRepository
import net.filmix.core.model.AppVersion
import net.filmix.core.model.UpdateState
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import net.filmix.core.data.PairingState

class ProfileViewModel(
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    val version: AppVersion,
    private val updates: UpdateRepository,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** Set once a download completes, so install can be retried without refetching. */
    var downloadedApk: File? = null
        private set

    fun checkForUpdate() {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            _updateState.value = runCatching { updates.check() }.fold(
                onSuccess = { if (it == null) UpdateState.UpToDate else UpdateState.Available(it) },
                onFailure = { UpdateState.Failed(it.message.orEmpty()) },
            )
        }
    }

    fun downloadUpdate() {
        val available = (_updateState.value as? UpdateState.Available)?.update ?: return
        viewModelScope.launch {
            runCatching {
                updates.download(available).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Running ->
                            _updateState.value = UpdateState.Downloading(available, progress.percent)

                        is DownloadProgress.Done -> {
                            downloadedApk = progress.file
                            _updateState.value = UpdateState.ReadyToInstall(available)
                        }
                    }
                }
            }.onFailure { _updateState.value = UpdateState.Failed(it.message.orEmpty()) }
        }
    }

    val preferredQuality: StateFlow<Int?> = settings.preferredQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPreferredQuality(quality: Int?) {
        viewModelScope.launch { settings.setPreferredQuality(quality) }
    }


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
