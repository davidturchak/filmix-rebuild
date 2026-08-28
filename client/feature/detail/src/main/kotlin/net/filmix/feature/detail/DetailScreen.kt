package net.filmix.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import net.filmix.core.designsystem.component.Rail
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.designsystem.theme.LocalIsTv
import net.filmix.core.designsystem.theme.ImdbGold
import net.filmix.core.designsystem.theme.KinopoiskOrange
import net.filmix.core.model.Comment
import net.filmix.core.model.CommentThread
import net.filmix.core.model.ParsedTranslation
import net.filmix.core.model.Episode
import net.filmix.core.model.Post
import net.filmix.core.model.StreamLink
import net.filmix.core.model.VideoSource

data class DetailUiState(
    val post: Post? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

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
    selection: EpisodeSelection = EpisodeSelection(),
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
                selection,
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
    selection: EpisodeSelection,
    onSelectSeason: (String) -> Unit,
    onSelectTranslation: (String) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
) {
    // Keyed on the post so opening a related title collapses the thread again.
    var commentsExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
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
                // A series has no single "play" source; the CTA starts the
                // first episode of the selected season and translation.
                firstEpisode = selection.resolve(post.playlist)?.second?.episodes?.firstOrNull(),
                onPlayEpisode = onPlayEpisode,
            )
        }

        if (!post.playlist.isEmpty) {
            item("episodes") {
                Section("Серии") {
                    EpisodePicker(
                        playlist = post.playlist,
                        selection = selection,
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
                    Text(
                        post.shortStory,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(if (compact) 1f else 0.75f),
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
            CommentsUiState.Loading -> item("comments") {
                Section("Отзывы") { CommentsNote("Загрузка…") }
            }

            CommentsUiState.Failed -> item("comments") {
                Section("Отзывы") { CommentsNote("Не удалось загрузить отзывы") }
            }

            is CommentsUiState.Loaded -> if (comments.threads.isEmpty()) {
                item("comments") {
                    Section("Отзывы") { CommentsNote("Никто еще не оставил отзывов.") }
                }
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
                        Box(Modifier.padding(horizontal = LocalDimensions.current.gutter)) {
                            TextButton(onClick = { commentsExpanded = !commentsExpanded }) {
                                Text(
                                    if (commentsExpanded) {
                                        "Свернуть"
                                    } else {
                                        "Показать все (${threads.sumOf { it.size }})"
                                    },
                                )
                            }
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
}

@Composable
private fun Backdrop(
    post: Post,
    compact: Boolean,
    onPlay: (VideoSource) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleWatchLater: () -> Unit,
    firstEpisode: Episode?,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val best = post.sources.firstOrNull()
                    if (best != null || firstEpisode != null) {
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
                                    firstEpisode != null -> onPlayEpisode(firstEpisode)
                                }
                            },
                            modifier = Modifier.focusRequester(playFocus),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(
                                if (best == null && firstEpisode != null) {
                                    "Смотреть · ${firstEpisode.label}"
                                } else {
                                    "Смотреть"
                                },
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(onClick = onToggleFavourite) {
                        Icon(
                            if (post.favorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "В избранное",
                        )
                    }
                    FilledTonalIconButton(onClick = onToggleWatchLater) {
                        Icon(
                            if (post.watchLater) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Смотреть позже",
                        )
                    }
                }
            }
        }
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = LocalDimensions.current.gutter)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

private const val COMMENTS_PREVIEW = 3

@Composable
private fun CommentsNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CommentThreadItem(thread: CommentThread, showTitle: Boolean = false) {
    Column(
        Modifier.padding(horizontal = LocalDimensions.current.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showTitle) {
            Text(
                "Отзывы",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        CommentRow(thread.root)
        thread.replies.forEach { reply ->
            CommentRow(reply, Modifier.padding(start = 24.dp))
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
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
