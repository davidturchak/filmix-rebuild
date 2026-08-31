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
import net.filmix.core.model.AppUpdate
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

    /** Null means "follow the device", which is what [voiceLanguages] offers last. */
    val voiceLanguage: StateFlow<String?> = settings.voiceLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setVoiceLanguage(tag: String?) {
        viewModelScope.launch { settings.setVoiceLanguage(tag) }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** Set once a download completes, so install can be retried without refetching. */
    var downloadedApk: File? = null
        private set

    /**
     * The update found by the launch check, until the user answers. Kept apart
     * from [updateState] so pressing "Проверить обновления" inside Настройки
     * never raises a dialog over the screen the user is already looking at.
     */
    private val _launchUpdate = MutableStateFlow<AppUpdate?>(null)
    val launchUpdate: StateFlow<AppUpdate?> = _launchUpdate.asStateFlow()

    init {
        refreshPlayers()
        checkOnLaunch()
    }

    /**
     * Silent by construction. On any failure — and on no network at all — this
     * leaves the state at Idle, so nothing appears on screen and the manual
     * button in Настройки still behaves exactly as before.
     */
    private fun checkOnLaunch() {
        viewModelScope.launch {
            val found = updates.checkQuietly() ?: return@launch
            // Asked once per release: "Позже" on 0.6.7 must not re-ask every
            // launch, but a later 0.6.8 must still get through.
            if (!found.shouldPrompt(settings.dismissedUpdate())) return@launch
            // Only while the user has started nothing of their own. This lands
            // up to several seconds after launch, by which time someone can
            // already be on Настройки with a check of their own — replacing
            // their Failed/Checking/Downloading state, and raising a modal over
            // the screen they are looking at, is not this check's business.
            if (_updateState.value != UpdateState.Idle) return@launch
            _updateState.value = UpdateState.Available(found)
            _launchUpdate.value = found
        }
    }

    /** "Позже" — remembered, so the prompt does not return for this release. */
    fun dismissLaunchUpdate() {
        val code = _launchUpdate.value?.versionCode ?: return
        _launchUpdate.value = null
        viewModelScope.launch { settings.setDismissedUpdate(code) }
    }

    /**
     * "Обновить" — closes the prompt without recording a dismissal, because the
     * user is being sent to Настройки to finish the job, not declining it.
     */
    fun acceptLaunchUpdate() {
        _launchUpdate.value = null
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
