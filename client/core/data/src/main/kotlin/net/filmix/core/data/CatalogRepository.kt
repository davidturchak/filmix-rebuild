package net.filmix.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.filmix.core.model.Post
import net.filmix.core.model.CatalogFilter
import net.filmix.core.model.CommentThread
import net.filmix.core.model.threadComments
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder
import net.filmix.core.network.FilmixApi
import net.filmix.core.network.dto.toDomain as postToDomain
import net.filmix.core.network.dto.toDomain

/**
 * Read access to the catalog. Every call returns at most one page; the backend
 * serves 50 items per page and has no total-count field, so "there is more"
 * is inferred from a full page coming back.
 */
class CatalogRepository(private val api: FilmixApi) {

    suspend fun newest(page: Int = 1): List<Post> = io {
        api.catalog(orderBy = "date", orderDir = "desc", page = page).map { it.toDomain() }
    }

    suspend fun popular(page: Int = 1, section: Int? = null): List<Post> = io {
        api.popular(page = page, section = section).map { it.toDomain() }
    }

    suspend fun topViews(page: Int = 1, section: Int? = null): List<Post> = io {
        api.topViews(page = page, section = section).map { it.toDomain() }
    }

    /** Empty until the device is paired — the backend ties history to the token. */
    suspend fun history(page: Int = 1): List<Post> = io {
        api.history(page = page).map { it.toDomain() }
    }

    suspend fun search(query: String, page: Int = 1): List<Post> = io {
        api.search(story = query, page = page).map { it.toDomain() }
    }

    /** Type-ahead results; deliberately unpaged and capped for a dropdown. */
    suspend fun suggest(word: String, limit: Int = 8): List<Post> = io {
        api.suggest(word).take(limit).map { it.toDomain() }
    }

    suspend fun catalog(
        sort: SortOrder = SortOrder.Default,
        direction: SortDirection = SortDirection.Default,
        filter: CatalogFilter = CatalogFilter(),
        page: Int = 1,
    ): List<Post> = io {
        api.catalog(
            orderBy = sort.apiValue,
            orderDir = direction.apiValue,
            filter = filter.toApiValue(),
            page = page,
        ).map { it.toDomain() }
    }

    /** Paged, sorted, filtered catalog for the browse grid. */
    fun catalogPager(
        sort: SortOrder,
        direction: SortDirection,
        filter: CatalogFilter = CatalogFilter(),
    ): Pager<Int, Post> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE),
        pagingSourceFactory = { CatalogPagingSource(this, sort, direction, filter) },
    )

    /** Filter choices; cached for the process since they rarely change. */
    suspend fun filterOptions(): FilterOptions = cachedFilters ?: io {
        api.filterList().toDomain().also { cachedFilters = it }
    }

    @Volatile
    private var cachedFilters: FilterOptions? = null

    /** Paged search results, for the results grid. */
    fun searchPager(query: String): Pager<Int, Post> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE),
        pagingSourceFactory = { SearchPagingSource(this, query) },
    )

    /**
     * Full detail. Catalog listings omit `short_story` and `player_links`, so
     * the hero needs this call to show a synopsis.
     */
    suspend fun post(id: Int): Post = io { api.post(id).toDomain() }

    /** The flattened comment payload rebuilt into display order; see [threadComments]. */
    suspend fun comments(postId: Int): List<CommentThread> = io {
        threadComments(api.comments(postId).map { it.toDomain() })
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

const val PAGE_SIZE = 50
