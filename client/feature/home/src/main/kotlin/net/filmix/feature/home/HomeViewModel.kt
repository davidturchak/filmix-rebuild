package net.filmix.feature.home

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.SessionState
import net.filmix.core.model.HomeRefresh
import net.filmix.core.model.Post
import net.filmix.core.model.RefreshPolicy

/**
 * Rails load concurrently and fail independently: one endpoint erroring leaves
 * the rest of the screen intact rather than blanking the whole page. An empty
 * rail is simply not rendered, which is also how the unpaired "continue
 * watching" case resolves — history returns [] until the device is linked.
 */
class HomeViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val session: SessionState,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * When a catalog rail last came back with something, on the monotonic
     * clock; null until one has. Only a load that landed moves it — a fetch
     * that failed leaves the rails as stale as they were.
     */
    private var catalogLoadedAt: Long? = null

    /**
     * When the catalog rails were last asked for, landed or not. Stamped
     * before the fetch so a resume that arrives while it is in flight does
     * not start a second one, and so a failure is not retried on every
     * glance — see [RefreshPolicy.RETRY_AFTER_MS].
     */
    private var catalogAttemptedAt: Long? = null

    init {
        refresh()
        // This screen is activity-scoped and there is no backstack, so without
        // these it kept the list it built at app start for the life of the
        // process: you would watch a film, come back to Home, and «Продолжить
        // просмотр» still did not have it — the one rail on the screen that is
        // supposed to be about what you just did.
        //
        // Only that rail reloads here. Nothing about watching a film changes
        // Новинки or Популярное; those age on their own clock instead, and
        // refreshIfStale refetches them in place once they are old enough.
        viewModelScope.launch {
            // drop(1): the revision on subscribing is not news, it is just the
            // count so far.
            library.revision.drop(1).collect { refreshContinueWatching() }
        }
        viewModelScope.launch {
            // Pairing cannot be seen in the token, so the rail built above was
            // built without knowing — and for a device that pairs mid-session
            // it was built against an endpoint that answers [] when unlinked.
            // Only worth a call when it can change what is on screen: a linked
            // account with no rail is exactly what entering the code leaves
            // behind.
            session.linked.filterNotNull().collect { linked ->
                if (linked && _state.value.rails.none { it.title == RAIL_CONTINUE }) {
                    refreshContinueWatching()
                }
            }
        }
    }

    fun refresh() {
        catalogAttemptedAt = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val fresh = fetchRails(RAIL_ORDER)
            noteCatalogLoaded(fresh)
            val rails = emptyList<HomeRail>().merged(fresh)
            _state.value = HomeUiState(
                featured = heroFor(newest = fresh[RAIL_NEW]?.firstOrNull(), keep = null),
                rails = rails,
                loading = false,
                error = if (rails.isEmpty()) "Не удалось загрузить каталог" else null,
            )
        }
    }

    /**
     * Refetches Новинки, Популярное and «Сейчас смотрят» if they are older
     * than [RefreshPolicy.STALE_AFTER_MS] — or never loaded — and leaves them
     * alone otherwise. Called on every resume of the Home tab, which includes
     * coming back to it from another tab, because the observer is added to an
     * already-resumed lifecycle; the thresholds are what stop it turning into
     * a reload per glance.
     *
     * A screen with rails on it is refreshed behind them, in place, like
     * [refreshContinueWatching]: the cards are keyed by rail and post id, so
     * the cursor stays on its card when that card survives, and the screen
     * hands it to a neighbour when it does not. The hero is only refetched
     * when the newest title has actually changed, since its detail is a
     * fifth request the rails do not need.
     *
     * A screen with nothing on it is showing «Повторить», and this presses it
     * — with the same retry gap, so an offline device does not flash the
     * spinner on every tab switch.
     */
    fun refreshIfStale() {
        val current = _state.value
        if (current.loading) return
        if (!RefreshPolicy.isDue(catalogLoadedAt, catalogAttemptedAt, SystemClock.elapsedRealtime())) return
        if (current.rails.isEmpty()) {
            refresh()
            return
        }
        catalogAttemptedAt = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            val fresh = fetchRails(CATALOG_RAILS)
            noteCatalogLoaded(fresh)
            val hero = heroFor(newest = fresh[RAIL_NEW]?.firstOrNull(), keep = _state.value.featured)
            _state.update { latest ->
                latest.copy(featured = hero, rails = latest.rails.merged(fresh), error = null)
            }
        }
    }

    private var continueJob: Job? = null

    /**
     * Reloads «Продолжить просмотр» in place, leaving the rest of the screen
     * exactly as it is.
     *
     * Two triggers can fire close together — finishing a film reports it and
     * can resolve the pairing state in the same breath — so the run in flight
     * is cancelled rather than left to land in an arbitrary order.
     *
     * No spinner: this runs while the user is looking at a screen that is
     * already populated, and blanking it to reload one rail would be a worse
     * answer than the stale rail this exists to replace.
     */
    private fun refreshContinueWatching() {
        continueJob?.cancel()
        continueJob = viewModelScope.launch {
            val fresh = fetchRails(listOf(RAIL_CONTINUE))
            _state.update { latest ->
                // The rail on screen is dropped before the merge, so that an
                // empty answer takes it off the screen rather than keeping the
                // old one: a history cleared from Профиль has to be able to
                // remove the rail, not just empty it.
                latest.copy(rails = latest.rails.filterNot { it.title == RAIL_CONTINUE }.merged(fresh))
            }
        }
    }

    /** Fetches the named rails concurrently; a rail whose request failed comes back empty. */
    private suspend fun fetchRails(titles: List<String>): Map<String, List<Post>> = coroutineScope {
        titles.map { title ->
            async { title to catalog.runCatchingList(RAIL_SOURCES.getValue(title)) }
        }.awaitAll()
    }.toMap()

    /** Stamps the catalog clock if [fresh] brought back any catalog rail — «Продолжить просмотр» does not count. */
    private fun noteCatalogLoaded(fresh: Map<String, List<Post>>) {
        if (CATALOG_RAILS.any { fresh[it].orEmpty().isNotEmpty() }) {
            catalogLoadedAt = SystemClock.elapsedRealtime()
        }
    }

    /**
     * The hero for a Новинки rail whose first entry is [newest]. Catalog
     * listings carry no synopsis, so its detail is fetched for the hero to
     * have something to say — unless [keep] is already that title, or there
     * is no newest to replace it with.
     */
    private suspend fun heroFor(newest: Post?, keep: Post?): Post? = when {
        newest == null || newest.id == keep?.id -> keep
        else -> runCatching { catalog.post(newest.id) }.getOrDefault(newest)
    }

    /** The rails in canonical order with [fresh] folded in — see [HomeRefresh.mergeRails]. */
    private fun List<HomeRail>.merged(fresh: Map<String, List<Post>>): List<HomeRail> =
        HomeRefresh.mergeRails(
            order = RAIL_ORDER,
            current = associate { it.title to it.items },
            fresh = fresh,
        ).map { (title, items) -> HomeRail(title, items) }

    private suspend fun CatalogRepository.runCatchingList(
        block: suspend CatalogRepository.() -> List<Post>,
    ): List<Post> = runCatching { block() }
        .onFailure { Log.w(TAG, "rail failed to load", it) }
        .getOrDefault(emptyList())

    private companion object {
        const val TAG = "HomeViewModel"
        const val RAIL_CONTINUE = "Продолжить просмотр"
        const val RAIL_NEW = "Новинки"
        const val RAIL_POPULAR = "Популярное"
        const val RAIL_TOP = "Сейчас смотрят"

        /** Every rail, in the order it appears on screen, with the request that fills it. */
        val RAIL_SOURCES: Map<String, suspend CatalogRepository.() -> List<Post>> = linkedMapOf(
            RAIL_CONTINUE to { history() },
            RAIL_NEW to { newest() },
            RAIL_POPULAR to { popular() },
            RAIL_TOP to { topViews() },
        )
        val RAIL_ORDER = RAIL_SOURCES.keys.toList()

        /** The rails that age on the site's clock, as opposed to the user's. */
        val CATALOG_RAILS = RAIL_ORDER - RAIL_CONTINUE
    }
}
