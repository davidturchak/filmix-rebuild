package net.filmix.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.rememberFocusReturn
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.describeError
import net.filmix.core.designsystem.component.rememberVoiceSearch
import net.filmix.core.designsystem.theme.LocalIsTv
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.model.Post

@Composable
fun SearchScreen(
    query: String,
    suggestions: List<Post>,
    results: LazyPagingItems<Post>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onClear: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onVoiceResult: (String) -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current

    // On a remote, typing means driving an on-screen grid key by key. Voice is
    // the faster path and is what the original app offered on TV.
    val voice = rememberVoiceSearch(prompt = "Что найти?", onResult = onVoiceResult)
    val micFocus = remember { FocusRequester() }
    val isTv = LocalIsTv.current

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimensions.current.gutter, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A focused text field consumes LEFT/RIGHT for the caret, so a
            // trailing mic is unreachable by remote. Leading it means the
            // D-pad meets it on the way in — and on TV voice is the primary
            // input anyway.
            if (voice.available && isTv) {
                MicButton(micFocus, voice.listening, voice::start)
            }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Фильмы, сериалы, актёры") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSubmit()
                    keyboard?.hide()
                },
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f),
        )
            if (voice.available && !isTv) {
                MicButton(micFocus, voice.listening, voice::start)
            }
        }

        // Suggestions replace the grid while typing; committing a query swaps
        // back to paged results.
        if (suggestions.isNotEmpty()) {
            Column(Modifier.padding(horizontal = LocalDimensions.current.gutter)) {
                suggestions.forEach { item ->
                    SuggestionRow(item, onClick = { onPostClick(item) })
                }
            }
            return@Column
        }

        voice.lastError?.let { code ->
            Text(
                describeError(code),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = LocalDimensions.current.gutter),
            )
        }

        ResultsGrid(results, compact, onPostClick)
    }
}

@Composable
private fun MicButton(
    focusRequester: FocusRequester,
    listening: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalIconButton(
        onClick = onClick,
        colors = if (listening) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        },
        interactionSource = interaction,
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusRing(interactionSource = interaction),
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Голосовой поиск")
    }
}

@Composable
private fun ResultsGrid(
    results: LazyPagingItems<Post>,
    compact: Boolean,
    onPostClick: (Post) -> Unit,
) {
    val refreshing = results.loadState.refresh is LoadState.Loading
    val failed = results.loadState.refresh is LoadState.Error
    val gridState = rememberLazyGridState()
    val focusReturn = rememberFocusReturn { index ->
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            gridState.scrollToItem(index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            refreshing -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            failed -> Text(
                "Не удалось выполнить поиск",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            results.itemCount == 0 -> Text(
                "Начните вводить название",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(
                    minSize = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                ),
                contentPadding = PaddingValues(LocalDimensions.current.gutter),
                horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.railGap),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
            ) {
                items(count = results.itemCount) { index ->
                    val post = results[index] ?: return@items
                    PosterCard(
                        modifier = focusReturn.modifier(index),
                        title = post.title,
                        posterUrl = post.posterUrl,
                        rating = post.rating,
                        subtitle = post.lastEpisode?.label
                            ?: post.year.takeIf { it > 0 }?.toString(),
                        width = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                        height = if (compact) LocalDimensions.current.posterHeightCompact else LocalDimensions.current.posterHeight,
                        onClick = {
                            focusReturn.opened(index)
                            onPostClick(post)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(post: Post, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = post.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 60.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                post.originalTitle.takeIf { it.isNotEmpty() && it != post.title },
                post.year.takeIf { it > 0 }?.toString(),
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
