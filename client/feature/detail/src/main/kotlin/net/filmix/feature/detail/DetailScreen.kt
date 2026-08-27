package net.filmix.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
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
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.Rail
import net.filmix.core.designsystem.theme.Dimens
import net.filmix.core.designsystem.theme.ImdbGold
import net.filmix.core.designsystem.theme.KinopoiskOrange
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

@Composable
fun DetailScreen(
    state: DetailUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
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

@Composable
private fun Content(
    post: Post,
    compact: Boolean,
    onPlay: (VideoSource) -> Unit,
    onRelatedClick: (Post) -> Unit,
    onToggleFavourite: () -> Unit,
    onToggleWatchLater: () -> Unit,
    selection: EpisodeSelection,
    onSelectSeason: (String) -> Unit,
    onSelectTranslation: (String) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = Dimens.sectionGap),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
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
                        width = if (compact) Dimens.posterWidthCompact else Dimens.posterWidth,
                        height = if (compact) Dimens.posterHeightCompact else Dimens.posterHeight,
                        onClick = { onRelatedClick(related) },
                    )
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
            .height(if (compact) Dimens.heroHeightCompact else Dimens.heroHeight),
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
                .padding(Dimens.gutter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!compact) {
                AsyncImage(
                    model = post.posterUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimens.posterWidth, Dimens.posterHeight)
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
                ChipRow {
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
                        val playInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                when {
                                    best != null -> onPlay(best)
                                    firstEpisode != null -> onPlayEpisode(firstEpisode)
                                }
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            interactionSource = playInteraction,
                            modifier = Modifier.focusRing(
                                shape = MaterialTheme.shapes.extraLarge,
                                interactionSource = playInteraction,
                            ),
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
    Column(Modifier.padding(horizontal = Dimens.gutter)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        content()
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
