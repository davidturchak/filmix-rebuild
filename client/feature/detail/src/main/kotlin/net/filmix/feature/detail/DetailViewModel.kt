package net.filmix.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.LibraryRepository

class DetailViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loadedId: Int? = null

    private val _selection = MutableStateFlow(EpisodeSelection())
    val selection: StateFlow<EpisodeSelection> = _selection.asStateFlow()

    fun selectSeason(season: String) {
        // Translations are season-specific, so clear it and let resolve() pick
        // the first one available in the newly chosen season.
        _selection.value = EpisodeSelection(season = season, translation = null)
    }

    fun selectTranslation(name: String) {
        _selection.value = _selection.value.copy(translation = name)
    }

    /**
     * Optimistic: the endpoints confirm success but do not report the resulting
     * flag, so the local copy is flipped immediately and reverted if the call
     * fails. That keeps the icon responsive on a slow connection.
     */
    fun toggleFavourite() = toggle(
        current = { it.favorited },
        apply = { post, value -> post.copy(favorited = value) },
        call = { library.toggleFavourite(it) },
    )

    fun toggleWatchLater() = toggle(
        current = { it.watchLater },
        apply = { post, value -> post.copy(watchLater = value) },
        call = { library.toggleWatchLater(it) },
    )

    private fun toggle(
        current: (net.filmix.core.model.Post) -> Boolean,
        apply: (net.filmix.core.model.Post, Boolean) -> net.filmix.core.model.Post,
        call: suspend (Int) -> Boolean,
    ) {
        val post = _state.value.post ?: return
        val next = !current(post)
        _state.value = _state.value.copy(post = apply(post, next))
        viewModelScope.launch {
            val ok = runCatching { call(post.id) }.getOrDefault(false)
            if (!ok) {
                _state.value.post?.let { latest ->
                    _state.value = _state.value.copy(post = apply(latest, !next))
                }
            }
        }
    }

    /** Idempotent: re-entering the same title does not refetch. */
    fun load(id: Int) {
        if (loadedId == id && _state.value.post != null) return
        loadedId = id
        viewModelScope.launch {
            _state.value = DetailUiState(loading = true)
            _selection.value = EpisodeSelection()
            _state.value = runCatching { catalog.post(id) }.fold(
                onSuccess = { DetailUiState(post = it, loading = false) },
                onFailure = { DetailUiState(loading = false, error = "Не удалось загрузить") },
            )
        }
    }
}
