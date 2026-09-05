package net.filmix.feature.catalog

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.SettingsStore
import net.filmix.core.model.CatalogFilter
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.Post
import net.filmix.core.model.RefreshPolicy
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder

/**
 * Sort, direction and filter travel together — any one changing must rebuild
 * the pager, and holding them in one value keeps that a single distinct state.
 */
data class CatalogQuery(
    val order: SortOrder = SortOrder.Default,
    val direction: SortDirection = SortDirection.Default,
    val filter: CatalogFilter = CatalogFilter(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(
    private val catalog: CatalogRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _sort = MutableStateFlow(CatalogQuery())
    val sort: StateFlow<CatalogQuery> = _sort.asStateFlow()

    private val _filterOptions = MutableStateFlow(FilterOptions())
    val filterOptions: StateFlow<FilterOptions> = _filterOptions.asStateFlow()

    /**
     * Counts the reloads [refreshIfStale] has asked for. The screen reads it
     * to tell a reload behind a populated grid from a sort change, which
     * shows the spinner as it always has.
     */
    private val _reloads = MutableStateFlow(0)
    val reloads: StateFlow<Int> = _reloads.asStateFlow()

    /**
     * When the grid last came back with a page, on the monotonic clock; null
     * until one has. Reported by the screen through [noteRefresh], because
     * only the presenter sees a page land. A failed load leaves it alone, so
     * a stale grid stays due.
     */
    private var loadedAt: Long? = null

    /**
     * When a pager was last built, for whatever reason — first collection, a
     * sort or filter change, a stale reload. Stamped where the pager is built
     * so a resume arriving while its first page is in flight does not start
     * a second one — see [RefreshPolicy.RETRY_AFTER_MS].
     */
    private var attemptedAt: Long? = null

    /**
     * Sorting is applied by the backend, so a change restarts paging from page
     * one: flatMapLatest cancels the in-flight pager and builds a new one.
     * Holding both fields in a single value means toggling direction alone
     * still produces a distinct state — a StateFlow would swallow a re-emit of
     * an unchanged sort order. A stale reload takes the same path with the
     * same query, which is what the reload counter is combined in for.
     */
    val items: Flow<PagingData<Post>> = combine(_sort, _reloads) { query, _ -> query }
        .flatMapLatest { (order, direction, filter) ->
            attemptedAt = SystemClock.elapsedRealtime()
            catalog.catalogPager(order, direction, filter).flow
        }
        .cachedIn(viewModelScope)

    init {
        // Restore the last choice so the tab opens where the user left it.
        viewModelScope.launch {
            _sort.value = CatalogQuery(
                order = SortOrder.fromApiValue(settings.catalogSort()),
                direction = if (settings.catalogAscending()) {
                    SortDirection.Asc
                } else {
                    SortDirection.Desc
                },
            )
        }
        // Filter choices are static enough to fetch once and cache.
        viewModelScope.launch {
            _filterOptions.value = runCatching { catalog.filterOptions() }
                .getOrDefault(FilterOptions())
        }
    }

    /**
     * Rebuilds the pager if the grid is older than
     * [RefreshPolicy.STALE_AFTER_MS], or its last load failed and the retry
     * gap has passed. Called on every resume of the Каталог tab — including a
     * return from another tab, since the observer is added to an
     * already-resumed lifecycle — so the policy is what stops it reloading
     * per glance.
     *
     * A grid that was never asked for is left to the collection that is
     * about to build its first pager: this can run before that collection
     * has started, and bumping the counter then would only build the pager
     * twice.
     */
    fun refreshIfStale() {
        if (attemptedAt == null) return
        if (!RefreshPolicy.isDue(loadedAt, attemptedAt, SystemClock.elapsedRealtime())) return
        _reloads.value++
    }

    /**
     * The presenter's refresh state, as the screen sees it change. A page
     * that landed is what makes the grid fresh; nothing else can tell.
     */
    fun noteRefresh(state: LoadState) {
        if (state is LoadState.NotLoading) loadedAt = SystemClock.elapsedRealtime()
    }

    fun setFilter(filter: CatalogFilter) {
        if (filter == _sort.value.filter) return
        _sort.value = _sort.value.copy(filter = filter)
    }

    fun clearFilter() = setFilter(CatalogFilter())

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
