package net.filmix.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.ResumeStore
import net.filmix.core.model.SeriesProgress
import net.filmix.core.model.WatchProgress

class DetailViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val resumeStore: ResumeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loadedId: Int? = null

    private val _selection = MutableStateFlow(EpisodeSelection())
    val selection: StateFlow<EpisodeSelection> = _selection.asStateFlow()

    private val progressPostId = MutableStateFlow<Int?>(null)

    /**
     * Local watch progress for the open post, keyed by stream key. Room re-emits
     * after every player save, which is what moves the checkmarks and the
     * "current episode" on return from playback — load() never refetches.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val progress: StateFlow<Map<String, WatchProgress>> = progressPostId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyMap()) else resumeStore.progressForPost(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Separate from DetailUiState: the post fetch assigns a whole new state
    // object, which would clobber a comments result that landed first.
    private val _comments = MutableStateFlow<CommentsUiState>(CommentsUiState.Loading)
    val comments: StateFlow<CommentsUiState> = _comments.asStateFlow()

    fun selectSeason(season: String) {
        // Translations are season-specific, so clear it and let resolve() pick
        // the first one available in the newly chosen season.
        _selection.value = EpisodeSelection(season = season, translation = null)
    }

    fun selectTranslation(name: String) {
        _selection.value = _selection.value.copy(translation = name)
    }

    /**
     * Optimistic: the endpoints confirm success but do not report the resulting
     * flag, so the local copy is flipped immediately and reverted if the call
     * fails. That keeps the icon responsive on a slow connection.
     */
    fun toggleFavourite() = toggle(
        current = { it.favorited },
        apply = { post, value -> post.copy(favorited = value) },
        call = { library.toggleFavourite(it) },
    )

    fun toggleWatchLater() = toggle(
        current = { it.watchLater },
        apply = { post, value -> post.copy(watchLater = value) },
        call = { library.toggleWatchLater(it) },
    )

    private fun toggle(
        current: (net.filmix.core.model.Post) -> Boolean,
        apply: (net.filmix.core.model.Post, Boolean) -> net.filmix.core.model.Post,
        call: suspend (Int) -> Boolean,
    ) {
        val post = _state.value.post ?: return
        val next = !current(post)
        _state.value = _state.value.copy(post = apply(post, next))
        viewModelScope.launch {
            val ok = runCatching { call(post.id) }.getOrDefault(false)
            if (!ok) {
                _state.value.post?.let { latest ->
                    _state.value = _state.value.copy(post = apply(latest, !next))
                }
            }
        }
    }

    /** Idempotent: re-entering the same title does not refetch. */
    fun load(id: Int) {
        // Independent of the post fetch: a dead comments endpoint must not
        // cost the user the page, and vice versa — including retry: comments
        // get another chance on re-entry even when the post is cached.
        loadComments(id)
        progressPostId.value = id
        if (loadedId == id && _state.value.post != null) return
        loadedId = id
        viewModelScope.launch {
            _state.value = DetailUiState(loading = true)
            _selection.value = EpisodeSelection()
            _state.value = runCatching { catalog.post(id) }.fold(
                onSuccess = { post ->
                    // Land on the season and translation the user last played,
                    // before the post is shown, so the picker never flashes
                    // season 1 first. Once only — a return from the player hits
                    // the loadedId guard, so a manual pick is never clobbered.
                    if (!post.playlist.isEmpty) {
                        val snapshot = resumeStore.progressForPost(id).first()
                        SeriesProgress.resumePoint(post.playlist, snapshot)?.let {
                            _selection.value = EpisodeSelection(it.season, it.translation)
                        }
                    }
                    DetailUiState(post = post, loading = false)
                },
                onFailure = { DetailUiState(loading = false, error = "Не удалось загрузить") },
            )
        }
    }

    // Which post the comments flow currently belongs to. The ViewModel is
    // activity-scoped and serves every title the user opens, so a slow
    // response for a previous post must not land on the current one.
    private var commentsId: Int? = null

    private fun loadComments(id: Int) {
        // Loaded and in-flight Loading results are kept; only Failed retries.
        if (commentsId == id && _comments.value !is CommentsUiState.Failed) return
        commentsId = id
        _comments.value = CommentsUiState.Loading
        viewModelScope.launch {
            val result = runCatching { catalog.comments(id) }.fold(
                onSuccess = { CommentsUiState.Loaded(it) },
                onFailure = { CommentsUiState.Failed },
            )
            // Dropped if another title was opened while this was in flight.
            if (commentsId == id) _comments.value = result
        }
    }
}
