package net.filmix.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.filmix.core.model.Post
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder

/**
 * Pages the full catalog under a given sort. Same "short page means last page"
 * inference as search — the backend reports no total count.
 */
class CatalogPagingSource(
    private val repository: CatalogRepository,
    private val sort: SortOrder,
    private val direction: SortDirection,
) : PagingSource<Int, Post>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Post> {
        val page = params.key ?: FIRST_PAGE
        return runCatching { repository.catalog(sort, direction, page) }.fold(
            onSuccess = { items ->
                LoadResult.Page(
                    data = items,
                    prevKey = if (page == FIRST_PAGE) null else page - 1,
                    nextKey = if (items.size < PAGE_SIZE) null else page + 1,
                )
            },
            onFailure = { LoadResult.Error(it) },
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Post>): Int? =
        state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
