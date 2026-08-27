package net.filmix.core.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.filmix.core.model.Post

/**
 * Pages the search endpoint.
 *
 * The backend reports no total count, so "there is more" is inferred from a
 * full page coming back — a short page is the last one. Pages are 1-indexed.
 */
class SearchPagingSource(
    private val repository: CatalogRepository,
    private val query: String,
) : PagingSource<Int, Post>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Post> {
        val page = params.key ?: FIRST_PAGE
        return runCatching { repository.search(query, page) }.fold(
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
