package net.filmix.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.theme.Dimens
import net.filmix.core.model.Post
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder

@Composable
fun CatalogScreen(
    items: LazyPagingItems<Post>,
    sort: SortOrder,
    direction: SortDirection,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onSortChange: (SortOrder) -> Unit = {},
    onDirectionToggle: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SortBar(sort, direction, onSortChange, onDirectionToggle)

        Box(Modifier.fillMaxSize()) {
            when {
                items.loadState.refresh is LoadState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                items.loadState.refresh is LoadState.Error -> Text(
                    "Не удалось загрузить каталог",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(
                        minSize = if (compact) Dimens.posterWidthCompact else Dimens.posterWidth,
                    ),
                    contentPadding = PaddingValues(Dimens.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.railGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                ) {
                    items(count = items.itemCount) { index ->
                        val post = items[index] ?: return@items
                        PosterCard(
                            title = post.title,
                            posterUrl = post.posterUrl,
                            quality = post.quality,
                            subtitle = post.lastEpisode?.label
                                ?: post.year.takeIf { it > 0 }?.toString(),
                            width = if (compact) {
                                Dimens.posterWidthCompact
                            } else {
                                Dimens.posterWidth
                            },
                            height = if (compact) {
                                Dimens.posterHeightCompact
                            } else {
                                Dimens.posterHeight
                            },
                            onClick = { onPostClick(post) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sort is server-side (`orderby`/`orderdir`), so changing a chip restarts
 * paging from page 1 rather than reordering what is already loaded.
 */
@Composable
private fun SortBar(
    sort: SortOrder,
    direction: SortDirection,
    onSortChange: (SortOrder) -> Unit,
    onDirectionToggle: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = Dimens.gutter, vertical = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortOrder.entries.forEach { option ->
            FilterChip(
                selected = option == sort,
                onClick = { onSortChange(option) },
                label = { Text(option.label) },
            )
        }
        TextButton(onClick = onDirectionToggle) {
            Icon(
                imageVector = if (direction == SortDirection.Desc) {
                    Icons.Filled.ArrowDownward
                } else {
                    Icons.Filled.ArrowUpward
                },
                contentDescription = direction.label,
            )
            Text(
                text = if (direction == SortDirection.Desc) "Убыв." else "Возр.",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
