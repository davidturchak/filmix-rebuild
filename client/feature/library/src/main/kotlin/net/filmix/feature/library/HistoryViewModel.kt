package net.filmix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.SessionState
import net.filmix.core.model.Post

class HistoryViewModel(
    private val library: LibraryRepository,
    private val session: SessionState,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState(loading = true))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        refresh()
        // See LibraryViewModel: activity-scoped, so pairing happens after this
        // screen has already decided it is signed out.
        viewModelScope.launch {
            session.linked.filterNotNull().collect { linked ->
                if (linked != _state.value.signedIn) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val signedIn = runCatching { library.isSignedIn() }.getOrDefault(false)
            if (!signedIn) {
                _state.value = HistoryUiState(loading = false, signedIn = false)
                return@launch
            }
            val items = runCatching { library.history() }
            _state.value = HistoryUiState(
                items = items.getOrDefault(emptyList()),
                loading = false,
                signedIn = true,
                // Otherwise a failed request reads as "История пуста".
                failed = items.isFailure,
            )
        }
    }

    /**
     * Optimistic, like the favourite toggles: the entry disappears at once and
     * is put back if the call fails, so a slow network does not make the tap
     * feel ignored.
     */
    fun remove(post: Post) {
        val before = _state.value.items
        _state.value = _state.value.copy(items = before.filterNot { it.id == post.id })
        viewModelScope.launch {
            val ok = runCatching { library.removeFromHistory(post.id) }.getOrDefault(false)
            if (!ok) _state.value = _state.value.copy(items = before)
        }
    }

    fun clearAll() {
        val before = _state.value.items
        _state.value = _state.value.copy(items = emptyList())
        viewModelScope.launch {
            val ok = runCatching { library.clearHistory() }.getOrDefault(false)
            if (!ok) _state.value = _state.value.copy(items = before)
        }
    }
}
