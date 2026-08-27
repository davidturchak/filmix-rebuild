package net.filmix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val paired = runCatching { library.isSignedIn() }.getOrDefault(false)
            if (!paired) {
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
