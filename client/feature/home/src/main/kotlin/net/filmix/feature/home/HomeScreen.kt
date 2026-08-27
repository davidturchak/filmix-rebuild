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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.Rail
import net.filmix.core.designsystem.theme.Dimens
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
                Button(
                    onClick = onRetry,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Повторить") }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
        contentPadding = PaddingValues(bottom = Dimens.sectionGap),
    ) {
        state.featured?.let { featured ->
            item(key = "hero") {
                Hero(
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
                    title = post.title,
                    posterUrl = post.posterUrl,
                    quality = post.quality,
                    subtitle = post.lastEpisode?.label
                        ?: post.year.takeIf { it > 0 }?.toString(),
                    width = if (compact) Dimens.posterWidthCompact else Dimens.posterWidth,
                    height = if (compact) Dimens.posterHeightCompact else Dimens.posterHeight,
                    onClick = { onPostClick(post) },
                )
            }
        }
    }
}

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
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) Dimens.heroHeightCompact else Dimens.heroHeight),
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
                .padding(Dimens.gutter)
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
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val playInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onPlayClick,
                    shape = MaterialTheme.shapes.extraLarge,
                    interactionSource = playInteraction,
                    modifier = Modifier.focusRing(
                        shape = MaterialTheme.shapes.extraLarge,
                        interactionSource = playInteraction,
                    ),
                ) {
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
