package net.filmix.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.component.AccentChip
import net.filmix.core.designsystem.component.ChipRow
import net.filmix.core.designsystem.component.MetaChip
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.FilledTonalIconButton
import net.filmix.core.designsystem.component.IconButton
import net.filmix.core.designsystem.component.PrimaryButton
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.rememberFocusInteraction
import net.filmix.core.designsystem.component.Rail
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.designsystem.theme.LocalIsTv
import net.filmix.core.designsystem.theme.ImdbGold
import net.filmix.core.designsystem.theme.KinopoiskOrange
import net.filmix.core.designsystem.theme.VoteDown
import net.filmix.core.designsystem.theme.VoteUp
import net.filmix.core.model.Comment
import net.filmix.core.model.CommentThread
import net.filmix.core.model.ParsedTranslation
import net.filmix.core.model.Episode
import net.filmix.core.model.Post
import net.filmix.core.model.SeriesProgress
import net.filmix.core.model.StreamLink
import net.filmix.core.model.VideoSource
import net.filmix.core.model.Vote
import net.filmix.core.model.VoteTally
import net.filmix.core.model.WatchProgress

data class DetailUiState(
    val post: Post? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /**
     * How this device voted. Never comes from the API — no endpoint reports
     * it — so it is read from the local store when the post loads.
     */
    val ownVote: Vote? = null,
) {
    val tally: VoteTally?
        get() = post?.let { VoteTally(it.ratePositive, it.rateNegative, ownVote) }
}

/**
 * Failed and Loaded(empty) are distinct on purpose: a dead request and a
 * title nobody has reviewed must not read the same.
 */
sealed interface CommentsUiState {
    data object Loading : CommentsUiState
    data object Failed : CommentsUiState
    data class Loaded(val threads: List<CommentThread>) : CommentsUiState
}

@Composable
fun DetailScreen(
    state: DetailUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    comments: CommentsUiState = CommentsUiState.Loading,
    onBack: () -> Unit = {},
    onPlay: (VideoSource) -> Unit = {},
    onRelatedClick: (Post) -> Unit = {},
    onToggleFavourite: () -> Unit = {},
    onToggleWatchLater: () -> Unit = {},
    onVote: (Vote) -> Unit = {},
    selection: EpisodeSelection = EpisodeSelection(),
    progress: Map<String, WatchProgress> = emptyMap(),
    onSelectSeason: (String) -> Unit = {},
    onSelectTranslation: (String) -> Unit = {},
    onPlayEpisode: (Episode) -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null || state.post == null -> Text(
                text = state.error ?: "Ничего не найдено",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> Content(
                state.post,
                compact,
                comments,
                onPlay,
                onRelatedClick,
                onToggleFavourite,
                onToggleWatchLater,
                state.ownVote,
                onVote,
                selection,
                progress,
                onSelectSeason,
                onSelectTranslation,
                onPlayEpisode,
            )
        }

        // Touch only. On a remote this arrow overlays the list, claims the
        // window's initial focus, and blocks D-pad traversal into the content;
        // BACK on the remote does the same job without the trap.
        if (!LocalIsTv.current) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun Content(
    post: Post,
    compact: Boolean,
    comments: CommentsUiState,
    onPlay: (VideoSource) -> Unit,
    onRelatedClick: (Post) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleWatchLater: () -> Unit,
    ownVote: Vote?,
    onVote: (Vote) -> Unit,
    selection: EpisodeSelection,
    progress: Map<String, WatchProgress>,
    onSelectSeason: (String) -> Unit,
    onSelectTranslation: (String) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
) {
    // Keyed on the post so opening a related title collapses the thread again.
    var commentsExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    // Same: the reader must not survive onto the next title's description.
    var readerOpen by rememberSaveable(post.id) { mutableStateOf(false) }
    val watch = remember(post.playlist, selection, progress) {
        selection.resolve(post.playlist)?.let { (season, translation) ->
            SeriesProgress.seasonWatch(season, translation, progress)
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = LocalDimensions.current.sectionGap),
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
    ) {
        item("hero") {
            Backdrop(
                post = post,
                compact = compact,
                onPlay = onPlay,
                onToggleFavourite = onToggleFavourite,
                onToggleWatchLater = onToggleWatchLater,
                tally = VoteTally(post.ratePositive, post.rateNegative, ownVote),
                onVote = onVote,
                // A series has no single "play" source; the CTA continues the
                // episode the user is on — or starts the next, or the first.
                currentEpisode = watch?.current,
                continueWatching = watch?.currentInProgress == true,
                onPlayEpisode = onPlayEpisode,
            )
        }

        if (!post.playlist.isEmpty) {
            item("episodes") {
                Section("Серии", padContent = false) {
                    EpisodePicker(
                        playlist = post.playlist,
                        selection = selection,
                        watch = watch,
                        contentPadding = PaddingValues(
                            horizontal = LocalDimensions.current.gutter,
                        ),
                        onSelectSeason = onSelectSeason,
                        onSelectTranslation = onSelectTranslation,
                        onPlayEpisode = onPlayEpisode,
                    )
                }
            }
        }

        if (post.shortStory.isNotEmpty()) {
            item("synopsis") {
                Section("Описание") {
                    Synopsis(
                        text = post.shortStory,
                        postId = post.id,
                        compact = compact,
                        onExpand = { readerOpen = true },
                    )
                }
            }
        }

        if (post.sources.isNotEmpty()) {
            item("sources") {
                Section("Озвучки и качество") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        post.sources.forEach { source ->
                            SourceRow(source, onClick = { onPlay(source) })
                        }
                    }
                }
            }
        }

        if (post.cast.isNotEmpty() || post.directors.isNotEmpty()) {
            item("credits") {
                Section("В ролях") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (post.directors.isNotEmpty()) {
                            CreditLine("Режиссёр", post.directors.joinToString(", "))
                        }
                        val names = (post.cast.map { it.name } + post.actors).distinct()
                        if (names.isNotEmpty()) {
                            CreditLine("Актёры", names.joinToString(", "))
                        }
                    }
                }
            }
        }

        if (post.related.isNotEmpty()) {
            item("related") {
                Rail(title = "Похожее", items = post.related, key = { it.id }) { related ->
                    PosterCard(
                        title = related.title,
                        posterUrl = related.posterUrl,
                        subtitle = related.year.takeIf { it > 0 }?.toString(),
                        width = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                        height = if (compact) LocalDimensions.current.posterHeightCompact else LocalDimensions.current.posterHeight,
                        onClick = { onRelatedClick(related) },
                    )
                }
            }
        }

        when (comments) {
            CommentsUiState.Loading -> commentsNote("Загрузка…")

            CommentsUiState.Failed -> commentsNote("Не удалось загрузить отзывы")

            is CommentsUiState.Loaded -> if (comments.threads.isEmpty()) {
                commentsNote("Никто еще не оставил отзывов.")
            } else {
                val threads = comments.threads
                // One lazy item per thread: replies stay tight under their
                // parent while the page-level sectionGap separates threads.
                threads.take(COMMENTS_PREVIEW).forEachIndexed { index, thread ->
                    item("comment-thread-${thread.root.id}") {
                        CommentThreadItem(thread, showTitle = index == 0)
                    }
                }
                if (threads.size > COMMENTS_PREVIEW) {
                    // The toggle keeps its key and position in both states, so
                    // the focused node never leaves composition on expand and
                    // one DOWN steps into the first revealed thread below it.
                    item("comments-toggle") {
                        // Full width on purpose: D-pad focus search scores
                        // candidates by 13·vertical² plus the horizontal
                        // centre offset squared, so a narrow left-aligned
                        // button sandwiched between full-width rows loses to
                        // the next row in both directions once the list is
                        // expanded — collapse became unreachable by remote.
                        TextButton(
                            onClick = { commentsExpanded = !commentsExpanded },
                            modifier = Modifier
                                .padding(horizontal = LocalDimensions.current.gutter)
                                .fillMaxWidth(),
                        ) {
                            Text(
                                if (commentsExpanded) {
                                    "Свернуть"
                                } else {
                                    "Показать все (${threads.sumOf { it.size }})"
                                },
                            )
                        }
                    }
                    if (commentsExpanded) {
                        threads.drop(COMMENTS_PREVIEW).forEach { thread ->
                            item("comment-thread-${thread.root.id}") {
                                CommentThreadItem(thread)
                            }
                        }
                    }
                }
            }
        }
    }

    if (readerOpen) {
        SynopsisReader(
            text = post.shortStory,
            compact = compact,
            onClose = { readerOpen = false },
        )
    }
}

/**
 * The description, clamped to a few lines with a way into the full text.
 *
 * It used to be the whole synopsis, inert, in one lazy item — and on a remote
 * that made it the one part of this screen nobody could read. The D-pad scrolls
 * a LazyColumn by hopping between focusable nodes, so with nothing focusable
 * here DOWN went from the hero straight to «Озвучки и качество» and the list
 * scrolled only far enough to show it: anything taller than the screen went
 * past in a single jump, unreachable in either direction. The button is the
 * focus stop that was missing, and [SynopsisReader] is where the text can
 * actually be paged.
 *
 * The button appears only when the text is genuinely cut off. A two-line
 * synopsis growing a «Читать далее» that reveals nothing is worse than no
 * button at all, and visual overflow is the only honest way to know.
 */
@Composable
private fun Synopsis(
    text: String,
    postId: Int,
    compact: Boolean,
    onExpand: () -> Unit,
) {
    var overflowed by remember(postId) { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = SYNOPSIS_PREVIEW_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { overflowed = it.hasVisualOverflow },
        modifier = Modifier.fillMaxWidth(if (compact) 1f else 0.75f),
    )
    if (overflowed) {
        // Full width for the same reason the comments toggle is: D-pad focus
        // search scores candidates by 13·vertical² plus the horizontal centre
        // offset squared, and the text above is inset to three quarters of the
        // screen — a button matching it would pay that minor-axis penalty
        // against full-width neighbours a whole section away. The Section
        // already applies the gutter, so this needs no padding of its own.
        TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth()) {
            Text("Читать далее")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Backdrop(
    post: Post,
    compact: Boolean,
    onPlay: (VideoSource) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleWatchLater: () -> Unit,
    tally: VoteTally,
    onVote: (Vote) -> Unit,
    currentEpisode: Episode?,
    continueWatching: Boolean,
    onPlayEpisode: (Episode) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) LocalDimensions.current.heroHeightCompact else LocalDimensions.current.heroHeight),
    ) {
        AsyncImage(
            model = post.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(LocalDimensions.current.gutter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!compact) {
                AsyncImage(
                    model = post.posterUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(LocalDimensions.current.posterWidth, LocalDimensions.current.posterHeight)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    post.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (post.originalTitle.isNotEmpty() && post.originalTitle != post.title) {
                    Text(
                        post.originalTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Facts as chips rather than the original's "Жанр: …" text rows.
                //
                // Weighted for the same reason the home hero's synopsis is: this
                // Box has a fixed height, so the Column measures in order and
                // the last child takes what is left. A chip per country *and*
                // per genre wraps to three rows on a TV, which left the play
                // button — the whole point of the screen — measured at nothing.
                // Chips are the decoration here, so chips are what gives way.
                ChipRow(Modifier.weight(1f, fill = false)) {
                    post.rip?.let { AccentChip(it) }
                    post.year.takeIf { it > 0 }?.let { MetaChip(it.toString()) }
                    post.duration.takeIf { it > 0 }?.let { MetaChip("$it мин") }
                    post.countries.forEach { MetaChip(it) }
                    post.categories.forEach { MetaChip(it) }
                    post.kpRating?.takeIf { it.isNotEmpty() && it != "0" }
                        ?.let { MetaChip("КП $it", accent = KinopoiskOrange) }
                    post.imdbRating?.takeIf { it.isNotEmpty() && it != "0" }
                        ?.let { MetaChip("IMDb $it", accent = ImdbGold) }
                }
                // Wraps rather than a Row: play + favourite + watch-later + two
                // thumbs and their counts measure past 400dp, and a Row hands
                // whatever overruns its width a zero-width constraint — on a
                // compact phone the dislike button vanished and could not be
                // tapped. The Column above measures this one at its natural
                // height (the weighted ChipRow takes what is left), so a second
                // line costs chips, not the actions.
                // No itemVerticalAlignment on this Compose version, so each
                // child centres itself against the taller play button.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val centred = Modifier.align(Alignment.CenterVertically)
                    val best = post.sources.firstOrNull()
                    if (best != null || currentEpisode != null) {
                        // On a remote the screen opens with focus on the back
                        // arrow, which overlays the list — focus search cannot
                        // descend from it into the LazyColumn, so the D-pad
                        // appears dead. Claim focus for the primary action
                        // instead; BACK still exits via the hardware key.
                        val playFocus = remember { FocusRequester() }
                        LaunchedEffect(post.id) {
                            // The requester must be attached before it can be
                            // used; yielding a frame avoids a silent no-op.
                            withFrameNanos { }
                            runCatching { playFocus.requestFocus() }
                        }
                        PrimaryButton(
                            onClick = {
                                when {
                                    best != null -> onPlay(best)
                                    currentEpisode != null -> onPlayEpisode(currentEpisode)
                                }
                            },
                            modifier = centred.focusRequester(playFocus),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(
                                when {
                                    best != null || currentEpisode == null -> "Смотреть"
                                    continueWatching -> "Продолжить · ${currentEpisode.label}"
                                    else -> "Смотреть · ${currentEpisode.label}"
                                },
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(onClick = onToggleFavourite, modifier = centred) {
                        Icon(
                            if (post.favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "В избранное",
                        )
                    }
                    FilledTonalIconButton(onClick = onToggleWatchLater, modifier = centred) {
                        Icon(
                            if (post.watchLater) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Смотреть позже",
                        )
                    }
                    VoteButton(Vote.Up, tally, onVote, centred)
                    VoteButton(Vote.Down, tally, onVote, centred)
                }
            }
        }
    }
}

/**
 * One thumb and its count.
 *
 * The count sits beside the button rather than inside it so the thumbs stay
 * the same round shape as favourite and watch-later, and so the row keeps four
 * plain D-pad stops — the labels are not focusable.
 *
 * Once this device has voted that way the container fills with the original
 * app's like/dislike colour, which swallows the accent focus ring; `ringColor`
 * is what puts a visible ring back on it at three metres.
 */
@Composable
private fun VoteButton(
    vote: Vote,
    tally: VoteTally,
    onVote: (Vote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = tally.own == vote
    val accent = if (vote == Vote.Up) VoteUp else VoteDown
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onVote(vote) },
            // null keeps the wrapper's own tonal default, which is what an
            // unchosen thumb wants.
            colors = if (!chosen) {
                null
            } else {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = accent,
                    contentColor = Color.White,
                )
            },
            ringColor = if (chosen) Color.White else null,
        ) {
            Icon(
                when {
                    vote == Vote.Up && chosen -> Icons.Filled.ThumbUp
                    vote == Vote.Up -> Icons.Outlined.ThumbUp
                    chosen -> Icons.Filled.ThumbDown
                    else -> Icons.Outlined.ThumbDown
                },
                contentDescription = if (vote == Vote.Up) "Нравится" else "Не нравится",
            )
        }
        Text(
            text = (if (vote == Vote.Up) tally.positive else tally.negative).toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (chosen) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * One playable source. The API gives a single opaque string per entry —
 * `"Дубляж [4K, SDR, ru, HDRezka]"` — which the original app printed verbatim.
 * Splitting it into a voice-over name plus tag chips, with the real maximum
 * quality resolved from the link itself, is the clearest win on this screen.
 */
@Composable
private fun SourceRow(source: VideoSource, onClick: () -> Unit) {
    val parsed = remember(source.rawTranslation) { ParsedTranslation.from(source.rawTranslation) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRing(shape = MaterialTheme.shapes.medium, interactionSource = interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                parsed.voice,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (parsed.tags.isNotEmpty()) {
                ChipRow { parsed.tags.forEach { MetaChip(it) } }
            }
        }
        source.bestQuality?.let { AccentChip(StreamLink.label(it)) }
    }
}

@Composable
private fun Section(
    title: String,
    /**
     * The episodes section opts out: its chip rows scroll, and a scroll
     * container clips the focus ring at its own bounds — so they span
     * full-bleed and re-apply the gutter as content padding themselves.
     */
    padContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val gutter = LocalDimensions.current.gutter
    Column {
        Box(Modifier.padding(horizontal = gutter)) { SectionTitle(title) }
        Spacer(Modifier.height(12.dp))
        if (padContent) {
            // A Column, not a Box: content that emits siblings must keep
            // stacking the way it did when this padding lived on the outer
            // Column, rather than piling up on top of each other.
            Column(Modifier.padding(horizontal = gutter)) { content() }
        } else {
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

private const val COMMENTS_PREVIEW = 3

/**
 * Enough of the description to tell whether the film is worth the rest, without
 * the section pushing «Озвучки и качество» off a 540dp screen.
 */
private const val SYNOPSIS_PREVIEW_LINES = 4

/** The Loading, Failed, and empty states share one note-under-title item. */
private fun LazyListScope.commentsNote(text: String) {
    item("comments") {
        Section("Отзывы") {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommentThreadItem(thread: CommentThread, showTitle: Boolean = false) {
    Column(
        Modifier.padding(horizontal = LocalDimensions.current.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Not wrapped in Section: the threads after the first carry no title,
        // so the first draws its own via the shared SectionTitle instead.
        if (showTitle) {
            SectionTitle("Отзывы")
        }
        CommentRow(thread.root)
        thread.replies.forEach { reply ->
            CommentRow(reply, Modifier.padding(start = 24.dp))
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, modifier: Modifier = Modifier) {
    val interaction = rememberFocusInteraction()
    Row(
        modifier
            .fillMaxWidth()
            // No lift: a full-width text block scaling up reads as a glitch,
            // and the ring alone is cue enough on a row this large.
            .focusRing(
                shape = MaterialTheme.shapes.medium,
                scaleWhenFocused = 1f,
                interactionSource = interaction,
            )
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Focusable on TV only: the D-pad scrolls a LazyColumn by hopping
            // between focusable nodes, so rows must be stepping stones or the
            // section flies past in one jump. On touch they stay inert.
            .then(
                if (LocalIsTv.current) {
                    Modifier.focusable(interactionSource = interaction)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (comment.avatarUrl != null) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    comment.author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    comment.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
