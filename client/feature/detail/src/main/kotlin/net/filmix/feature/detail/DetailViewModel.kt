package net.filmix.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.filmix.core.data.CatalogRepository
import net.filmix.core.data.LibraryRepository
import net.filmix.core.data.RatingRepository
import net.filmix.core.data.ResumeStore
import net.filmix.core.model.SeriesProgress
import net.filmix.core.model.Vote
import net.filmix.core.model.VoteTally
import net.filmix.core.model.WatchProgress

class DetailViewModel(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    private val rating: RatingRepository,
    private val resumeStore: ResumeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var loadedId: Int? = null

    // The vote in flight, so a newer tap — or a fresh load — can retire it.
    private var voteJob: Job? = null

    /**
     * The last tally nobody is guessing about: what the post fetch returned,
     * or what a vote reply confirmed. A failed vote falls back here rather
     * than to whatever an earlier, still-unconfirmed tap left on screen —
     * reverting to that would leave a thumb filled for a vote the server
     * never took and the store never recorded.
     */
    private var confirmedTally: VoteTally? = null

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

    /**
     * Casts a vote and takes the server's word for the resulting counts — the
     * rate endpoint answers with the new totals, so unlike the toggles there
     * is nothing to guess at once the reply lands. Until then the local tally
     * moves so the thumb responds on a slow connection.
     *
     * Voting the same way twice sends nothing: the API has no un-vote.
     */
    fun vote(vote: Vote) {
        val post = _state.value.post ?: return
        val before = _state.value.tally ?: return
        if (before.own == vote) return
        val fallback = confirmedTally ?: before
        applyTally(post.id, before.optimistic(vote))
        // Correcting a mis-tapped thumb fires a second call while the first is
        // still out; without this the two replies land in whatever order the
        // network returns them and the older totals can win, leaving the
        // thumbs showing the side the user just moved away from.
        voteJob?.cancel()
        voteJob = viewModelScope.launch {
            val result = runCatching { rating.rate(post.id, vote) }.getOrNull()
            // Null covers both a thrown call and a refusal — the server omits
            // the counts rather than failing, so there is nothing to show but
            // the last tally that was actually confirmed.
            if (result != null) confirmedTally = result
            applyTally(post.id, result ?: fallback)
        }
    }

    /**
     * Dropped unless [postId] is still the open title: the ViewModel is
     * activity-scoped and serves every title the user opens, so a reply for a
     * post they have already left must not land on this one.
     */
    private fun applyTally(postId: Int, tally: VoteTally) {
        val post = _state.value.post ?: return
        if (post.id != postId) return
        _state.value = _state.value.copy(
            post = post.copy(
                ratePositive = tally.positive,
                rateNegative = tally.negative,
                // Nothing renders this post's score today, but it is derived
                // from the two counts above and a stale copy would be wrong
                // the moment a badge is put on this screen.
                rating = tally.net,
            ),
            ownVote = tally.own,
        )
    }

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
        // The counts are about to be refetched, so a reply for the vote cast
        // before this load must not write its stale totals over them.
        voteJob?.cancel()
        confirmedTally = null
        viewModelScope.launch {
            _state.value = DetailUiState(loading = true)
            _selection.value = EpisodeSelection()
            val result = runCatching { catalog.post(id) }
            // The ViewModel serves every title the user opens, so a slow
            // response for a post the user has already left must not clobber
            // the current one — the same guard loadComments carries.
            if (loadedId != id) return@launch
            _state.value = result.fold(
                onSuccess = { post ->
                    // Land on the season and translation the user last played,
                    // before the post is shown, so the picker never flashes
                    // season 1 first. Once only — a return from the player hits
                    // the loadedId guard, so a manual pick is never clobbered.
                    if (!post.playlist.isEmpty) {
                        // Progress is cosmetic; a failed read must not cost
                        // the user the page.
                        val snapshot = runCatching { resumeStore.progressSnapshotForPost(id) }
                            .getOrDefault(emptyMap())
                        SeriesProgress.resumePoint(post.playlist, snapshot)?.let {
                            _selection.value = EpisodeSelection(it.season, it.translation)
                        }
                    }
                    // Local only — no endpoint reports how this device
                    // voted, so a missing store read just leaves both thumbs
                    // plain rather than costing the user the page.
                    val own = runCatching { rating.ownVote(id) }.getOrNull()
                    confirmedTally = VoteTally(post.ratePositive, post.rateNegative, own)
                    DetailUiState(post = post, loading = false, ownVote = own)
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
