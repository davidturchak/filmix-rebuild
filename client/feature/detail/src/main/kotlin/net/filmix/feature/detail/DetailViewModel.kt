package net.filmix.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository

class DetailViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loadedId: Int? = null

    /** Idempotent: re-entering the same title does not refetch. */
    fun load(id: Int) {
        if (loadedId == id && _state.value.post != null) return
        loadedId = id
        viewModelScope.launch {
            _state.value = DetailUiState(loading = true)
            _state.value = runCatching { catalog.post(id) }.fold(
                onSuccess = { DetailUiState(post = it, loading = false) },
                onFailure = { DetailUiState(loading = false, error = "Не удалось загрузить") },
            )
        }
    }
}
