package net.filmix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.filmix.core.data.LibraryRepository

class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState(loading = true))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val paired = runCatching { library.isSignedIn() }.getOrDefault(false)
            if (!paired) {
                _state.value = _state.value.copy(loading = false, paired = false)
                return@launch
            }
            // Both lists load together so switching tabs is instant.
            val (favourites, watchLater) = coroutineScope {
                val fav = async { runCatching { library.favourites() }.getOrDefault(emptyList()) }
                val wl = async { runCatching { library.watchLater() }.getOrDefault(emptyList()) }
                fav.await() to wl.await()
            }
            _state.value = _state.value.copy(
                favourites = favourites,
                watchLater = watchLater,
                loading = false,
                paired = true,
            )
        }
    }

    fun selectTab(tab: LibraryTab) {
        _state.value = _state.value.copy(tab = tab)
    }
}
