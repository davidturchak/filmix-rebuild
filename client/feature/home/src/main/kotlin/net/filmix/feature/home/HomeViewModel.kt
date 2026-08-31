package net.filmix.feature.home

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
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.SessionState
import net.filmix.core.model.Post

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

    init {
        refresh()
        // This screen is activity-scoped and there is no backstack, so without
        // these it kept the list it built at app start for the life of the
        // process: you would watch a film, come back to Home, and «Продолжить
        // просмотр» still did not have it — the one rail on the screen that is
        // supposed to be about what you just did.
        //
        // Only that rail reloads, never the whole page. Nothing about watching
        // a film changes Новинки or Популярное, and a full refresh would
        // re-fetch four endpoints plus the hero's detail and shuffle every rail
        // under whatever the D-pad was pointing at.
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
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val sections = coroutineScope {
                listOf(
                    async { RAIL_CONTINUE to catalog.runCatchingList { history() } },
                    async { RAIL_NEW to catalog.runCatchingList { newest() } },
                    async { RAIL_POPULAR to catalog.runCatchingList { popular() } },
                    async { RAIL_TOP to catalog.runCatchingList { topViews() } },
                ).awaitAll()
            }

            val rails = sections
                .filter { (_, items) -> items.isNotEmpty() }
                .map { (title, items) -> HomeRail(title, items) }

            // The newest entry makes the hero, but catalog listings carry no
            // synopsis — fetch its detail so the hero has something to say.
            val heroSummary = sections.firstOrNull { it.first == RAIL_NEW }?.second?.firstOrNull()
            val hero = heroSummary?.let { summary ->
                runCatching { catalog.post(summary.id) }.getOrDefault(summary)
            }

            _state.value = HomeUiState(
                featured = hero,
                rails = rails,
                loading = false,
                error = if (rails.isEmpty()) "Не удалось загрузить каталог" else null,
            )
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
            val items = catalog.runCatchingList { history() }
            _state.value = _state.value.withContinueWatching(items)
        }
    }

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

        /**
         * The rail rebuilt into place: first when there is something to
         * continue, gone when there is not — a history cleared from Профиль
         * has to be able to take it off the screen, not just empty it.
         */
        fun HomeUiState.withContinueWatching(items: List<Post>): HomeUiState {
            val rest = rails.filterNot { it.title == RAIL_CONTINUE }
            return copy(
                rails = if (items.isEmpty()) rest else listOf(HomeRail(RAIL_CONTINUE, items)) + rest,
            )
        }
    }
}
