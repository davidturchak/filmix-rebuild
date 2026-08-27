package net.filmix.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.model.Post

/**
 * Rails load concurrently and fail independently: one endpoint erroring leaves
 * the rest of the screen intact rather than blanking the whole page. An empty
 * rail is simply not rendered, which is also how the unpaired "continue
 * watching" case resolves — history returns [] until the device is linked.
 */
class HomeViewModel(private val catalog: CatalogRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
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
    }
}
