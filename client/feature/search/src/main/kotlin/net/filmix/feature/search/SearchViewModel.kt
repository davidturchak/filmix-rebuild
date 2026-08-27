package net.filmix.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import net.filmix.core.data.CatalogRepository
import net.filmix.core.model.Post

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<Post>>(emptyList())
    val suggestions: StateFlow<List<Post>> = _suggestions.asStateFlow()

    /** Set only when the user commits a query; typing alone shows suggestions. */
    private val submitted = MutableStateFlow("")

    // StateFlow already conflates, so no distinctUntilChanged is needed here.
    val results: Flow<PagingData<Post>> = submitted
        .flatMapLatest { term ->
            if (term.length < MIN_QUERY) {
                flowOf(PagingData.empty())
            } else {
                catalog.searchPager(term).flow
            }
        }
        .cachedIn(viewModelScope)

    init {
        // Type-ahead is debounced so a fast typist issues one request, not ten.
        _query
            .debounce(SUGGEST_DEBOUNCE_MS)
            .distinctUntilChanged()
            .map { term -> term.trim() }
            .onEach { term ->
                _suggestions.value = if (term.length < MIN_QUERY || term == submitted.value) {
                    // Nothing to suggest for a term already searched — the grid
                    // below is showing its results. This is not hypothetical
                    // tidiness: a spoken query writes the term into _query to
                    // display it, which starts this pipeline, and the screen
                    // shows suggestions *instead of* results whenever any
                    // exist. Voice results appeared and were then replaced by a
                    // suggestion list a debounce later.
                    emptyList()
                } else {
                    runCatching { catalog.suggest(term) }.getOrDefault(emptyList())
                }
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _query.value = value
        if (value.isBlank()) submitted.value = ""
    }

    /** A spoken query is already complete, so search at once. */
    fun submitVoiceResult(text: String) {
        val term = text.trim()
        if (term.length < MIN_QUERY) return
        _query.value = term
        submitted.value = term
        _suggestions.value = emptyList()
    }

    fun submit() {
        val term = _query.value.trim()
        if (term.length >= MIN_QUERY) {
            submitted.value = term
            _suggestions.value = emptyList()
        }
    }

    fun clear() {
        _query.value = ""
        submitted.value = ""
        _suggestions.value = emptyList()
    }

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 300L
        const val MIN_QUERY = 2
    }
}
