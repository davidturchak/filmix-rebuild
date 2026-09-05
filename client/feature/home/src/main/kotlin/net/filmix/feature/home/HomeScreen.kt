package net.filmix.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.PrimaryButton
import net.filmix.core.designsystem.component.rememberFocusReturn
import net.filmix.core.designsystem.component.Rail
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.model.Post

data class HomeRail(val title: String, val items: List<Post>)

data class HomeUiState(
    val featured: Post? = null,
    val rails: List<HomeRail> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPostClick: (Post) -> Unit = {},
    onPlayClick: (Post) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    if (state.loading && state.rails.isEmpty()) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (state.error != null && state.rails.isEmpty()) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.error,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton(
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Повторить") }
            }
        }
        return
    }

    // Keyed by rail *and* post id. The id alone is not unique on this screen —
    // the same film turns up in "Продолжить просмотр" and "Популярное" both —
    // and two cards claiming the requester sends focus, and the scroll chasing
    // it, into whichever rail Compose reached first. An index would not do
    // either: a rail's contents shift under it on a refresh.
    val focusReturn = rememberFocusReturn()
    val listState = rememberLazyListState()

    // Which card holds the cursor, as rail title and post id. Not snapshot
    // state: a D-pad move between cards must not recompose the screen. It is
    // read only when the rails change, to see whether that card survived —
    // a refresh landing behind a populated screen can drop the very film the
    // cursor is on, and Compose then drops the cursor with it. Captured as the
    // new rails compose, before the lazy list measures them and detaches the
    // dropped card, since the detach may already have cleared the record.
    val focusedCard = remember { arrayOfNulls<Pair<String, Int>>(1) }
    val focusedBefore = remember(state.rails) { focusedCard[0] }
    LaunchedEffect(state.rails) {
        val (title, id) = focusedBefore ?: return@LaunchedEffect
        if (state.rails.any { it.title == title && it.items.any { post -> post.id == id } }) return@LaunchedEffect
        // Gone: hand the cursor to the head of the same rail, or of the first
        // one if the whole rail went, rather than let it fall to the hero and
        // pin the list back to the top from wherever the user was.
        val rail = state.rails.firstOrNull { it.title == title } ?: state.rails.firstOrNull() ?: return@LaunchedEffect
        focusReturn.restore(cardKey(rail.title, rail.items.first().id))
    }

    /**
     * The hero is the top item and its only focusable is the play button on
     * its bottom edge. Compose brings that button into view by scrolling the
     * list ~62dp, which crops the title off the top of the screen — and once
     * there, nothing above it is focusable, so the D-pad cannot scroll back:
     * the top of the home screen becomes unreachable for the rest of the
     * session. Pin the list at the top for as long as the hero holds focus,
     * rather than scrolling once — the focus event arrives before the scroll
     * it triggers, so a single correction would simply be undone.
     */
    var heroFocused by remember { mutableStateOf(false) }
    LaunchedEffect(heroFocused) {
        if (!heroFocused) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index > 0 || offset > 0) listState.scrollToItem(0)
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
        contentPadding = PaddingValues(bottom = LocalDimensions.current.sectionGap),
    ) {
        state.featured?.let { featured ->
            item(key = "hero") {
                Hero(
                    modifier = Modifier.onFocusEvent { heroFocused = it.hasFocus },
                    post = featured,
                    compact = compact,
                    onPlayClick = { onPlayClick(featured) },
                    onDetailsClick = { onPostClick(featured) },
                )
            }
        }
        items(
            count = state.rails.size,
            key = { index -> state.rails[index].title },
        ) { index ->
            val rail = state.rails[index]
            Rail(
                title = rail.title,
                items = rail.items,
                key = { it.id },
            ) { post ->
                PosterCard(
                    modifier = focusReturn
                        .modifier(cardKey(rail.title, post.id))
                        .onFocusChanged { focus ->
                            val card = rail.title to post.id
                            if (focus.hasFocus) focusedCard[0] = card
                            else if (focusedCard[0] == card) focusedCard[0] = null
                        },
                    title = post.title,
                    posterUrl = post.posterUrl,
                    rating = post.rating,
                    subtitle = post.lastEpisode?.label
                        ?: post.year.takeIf { it > 0 }?.toString(),
                    width = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                    height = if (compact) LocalDimensions.current.posterHeightCompact else LocalDimensions.current.posterHeight,
                    onClick = {
                        focusReturn.opened(cardKey(rail.title, post.id))
                        onPostClick(post)
                    },
                )
            }
        }
    }
}

/** The focus-return key of one card: unique across the screen, which a post id alone is not. */
private fun cardKey(rail: String, postId: Int) = "$rail/$postId"

/**
 * Full-bleed backdrop with the title, a metadata line and a single primary
 * action. Replaces the original home screen, which opened straight into a
 * text-dense list occupying about a quarter of the tablet's width.
 */
@Composable
private fun Hero(
    post: Post,
    compact: Boolean,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(if (compact) LocalDimensions.current.heroHeightCompact else LocalDimensions.current.heroHeight),
    ) {
        AsyncImage(
            model = post.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim so the title stays legible over arbitrary artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(LocalDimensions.current.gutter)
                .fillMaxWidth(if (compact) 1f else 0.55f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = post.title,
                style = if (compact) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                post.year.takeIf { it > 0 }?.let { add(it.toString()) }
                post.countries.firstOrNull()?.let(::add)
                post.categories.take(2).forEach(::add)
                post.quality?.let(::add)
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (post.shortStory.isNotEmpty() && !compact) {
                Text(
                    text = post.shortStory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // The hero's height is fixed, so a Column measures its
                    // children in order and the last one gets whatever is left
                    // — which for a two-line title plus three lines of synopsis
                    // was nothing: the play button was squeezed to a 12dp strip
                    // with its label clipped away. Weighting the synopsis makes
                    // it the child that yields instead, since it is the one
                    // already truncated by maxLines.
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(onClick = onPlayClick) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = "Смотреть",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(Modifier.width(0.dp))
            }
        }
    }
}
