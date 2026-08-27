package net.filmix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.SessionState

class LibraryViewModel(
    private val library: LibraryRepository,
    private val session: SessionState,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState(loading = true))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        refresh()
        // This screen is activity-scoped, so it outlives the pairing it was
        // built before: without this it kept the answer it got at construction
        // and stayed empty for the rest of the process once the user entered
        // their code. Only act on a disagreement, so the profile screen merely
        // confirming what is already on screen costs nothing.
        viewModelScope.launch {
            session.linked.filterNotNull().collect { linked ->
                if (linked != _state.value.paired) refresh()
            }
        }
        // Favouriting happens on the detail screen, which has no idea this one
        // exists. drop(1) because the current revision on subscribing is not a
        // change — only what follows is.
        viewModelScope.launch {
            library.revision.drop(1).collect { refresh() }
        }
    }

    private var loadJob: Job? = null

    fun refresh() {
        // Cancel the run in flight. There are four triggers now — init, the
        // session signal, a library revision, and Retry — and two of them fire
        // together when the user favourites something. Left to race, the slower
        // read could land last and put a stale list on screen with nothing to
        // correct it.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            // Three outcomes, not two: a failure to *ask* is not a no. Folding
            // it into "not paired" told a linked user to sign in — with no
            // retry, and no recovery, since nothing will re-emit for them.
            val paired = runCatching { library.isSignedIn() }
            if (paired.isFailure) {
                _state.value = _state.value.copy(
                    loading = false,
                    failed = LibraryTab.entries.toSet(),
                )
                return@launch
            }
            if (paired.getOrDefault(false).not()) {
                _state.value =
                    _state.value.copy(loading = false, paired = false, failed = emptySet())
                return@launch
            }
            // Both lists load together so switching tabs is instant.
            val (favourites, watchLater) = coroutineScope {
                val fav = async { runCatching { library.favourites() } }
                val wl = async { runCatching { library.watchLater() } }
                fav.await() to wl.await()
            }
            // A failed call used to fall back to an empty list, which the screen
            // then reported as "nothing in favourites" — a broken account-scoped
            // request and a genuinely empty list looked identical.
            _state.value = _state.value.copy(
                favourites = favourites.getOrDefault(emptyList()),
                watchLater = watchLater.getOrDefault(emptyList()),
                loading = false,
                paired = true,
                failed = buildSet {
                    if (favourites.isFailure) add(LibraryTab.Favourites)
                    if (watchLater.isFailure) add(LibraryTab.WatchLater)
                },
            )
        }
    }

    fun selectTab(tab: LibraryTab) {
        _state.value = _state.value.copy(tab = tab)
    }
}
