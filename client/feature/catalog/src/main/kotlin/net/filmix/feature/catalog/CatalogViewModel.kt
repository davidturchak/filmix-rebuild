package net.filmix.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.SettingsStore
import net.filmix.core.model.Post
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder

/** Sort and direction travel together — either one changing must rebuild the pager. */
data class CatalogSort(
    val order: SortOrder = SortOrder.Default,
    val direction: SortDirection = SortDirection.Default,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val catalog: CatalogRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _sort = MutableStateFlow(CatalogSort())
    val sort: StateFlow<CatalogSort> = _sort.asStateFlow()

    /**
     * Sorting is applied by the backend, so a change restarts paging from page
     * one: flatMapLatest cancels the in-flight pager and builds a new one.
     * Holding both fields in a single value means toggling direction alone
     * still produces a distinct state — a StateFlow would swallow a re-emit of
     * an unchanged sort order.
     */
    val items: Flow<PagingData<Post>> = _sort
        .flatMapLatest { (order, direction) -> catalog.catalogPager(order, direction).flow }
        .cachedIn(viewModelScope)

    init {
        // Restore the last choice so the tab opens where the user left it.
        viewModelScope.launch {
            _sort.value = CatalogSort(
                order = SortOrder.fromApiValue(settings.catalogSort()),
                direction = if (settings.catalogAscending()) {
                    SortDirection.Asc
                } else {
                    SortDirection.Desc
                },
            )
        }
    }

    fun setSort(order: SortOrder) {
        if (order == _sort.value.order) return
        _sort.value = _sort.value.copy(order = order)
        persist()
    }

    fun toggleDirection() {
        _sort.value = _sort.value.copy(direction = _sort.value.direction.toggled())
        persist()
    }

    private fun persist() {
        val current = _sort.value
        viewModelScope.launch {
            settings.setCatalogSort(current.order.apiValue)
            settings.setCatalogAscending(current.direction == SortDirection.Asc)
        }
    }
}
