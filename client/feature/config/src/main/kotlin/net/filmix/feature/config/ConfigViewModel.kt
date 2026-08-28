package net.filmix.feature.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.filmix.core.data.DownloadProgress
import net.filmix.core.data.SettingsStore
import net.filmix.core.data.UpdateRepository
import net.filmix.core.model.AppVersion
import net.filmix.core.model.ExternalPlayer
import net.filmix.core.model.UpdateState
import java.io.File

class ConfigViewModel(
    private val settings: SettingsStore,
    val version: AppVersion,
    private val updates: UpdateRepository,
    /** Injected as a lambda so PackageManager stays out of this module. */
    private val installedPlayers: suspend () -> List<ExternalPlayer>,
) : ViewModel() {

    val preferredQuality: StateFlow<Int?> = settings.preferredQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPreferredQuality(quality: Int?) {
        viewModelScope.launch { settings.setPreferredQuality(quality) }
    }

    private val _players = MutableStateFlow<List<ExternalPlayer>>(emptyList())
    val players: StateFlow<List<ExternalPlayer>> = _players.asStateFlow()

    /** Also called on resume: players get installed and removed in other apps. */
    fun refreshPlayers() {
        viewModelScope.launch { _players.value = installedPlayers() }
    }

    val selectedPlayer: StateFlow<String?> = settings.externalPlayerPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPlayer(packageName: String?) {
        viewModelScope.launch { settings.setExternalPlayerPackage(packageName) }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** Set once a download completes, so install can be retried without refetching. */
    var downloadedApk: File? = null
        private set

    init {
        refreshPlayers()
    }

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
}
