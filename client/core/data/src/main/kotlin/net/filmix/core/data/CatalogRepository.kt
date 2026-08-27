package net.filmix.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.filmix.core.model.Post
import net.filmix.core.network.FilmixApi
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

    /**
     * Full detail. Catalog listings omit `short_story` and `player_links`, so
     * the hero needs this call to show a synopsis.
     */
    suspend fun post(id: Int): Post = io { api.post(id).toDomain() }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}

const val PAGE_SIZE = 50
