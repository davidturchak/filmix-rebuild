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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.theme.Dimens
import net.filmix.core.model.Post
import net.filmix.core.model.CatalogFilter
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder

@Composable
fun CatalogScreen(
    items: LazyPagingItems<Post>,
    sort: SortOrder,
    direction: SortDirection,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    filter: CatalogFilter = CatalogFilter(),
    filterOptions: FilterOptions = FilterOptions(),
    onSortChange: (SortOrder) -> Unit = {},
    onDirectionToggle: () -> Unit = {},
    onFilterChange: (CatalogFilter) -> Unit = {},
    onClearFilter: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
) {
    var showFilters by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SortBar(
            sort = sort,
            direction = direction,
            activeFilters = filter.activeCount,
            filtersEnabled = !filterOptions.isEmpty,
            onSortChange = onSortChange,
            onDirectionToggle = onDirectionToggle,
            onOpenFilters = { showFilters = true },
        )

        if (showFilters) {
            FilterSheet(
                options = filterOptions,
                filter = filter,
                onFilterChange = onFilterChange,
                onClear = onClearFilter,
                onDismiss = { showFilters = false },
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                items.loadState.refresh is LoadState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                // Error must be checked before "no results": a failed load also
                // has an item count of zero.
                items.loadState.refresh is LoadState.Error -> Text(
                    "Не удалось загрузить каталог",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                items.itemCount == 0 -> Text(
                    "Ничего не найдено по выбранным фильтрам",
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
    activeFilters: Int,
    filtersEnabled: Boolean,
    onSortChange: (SortOrder) -> Unit,
    onDirectionToggle: () -> Unit,
    onOpenFilters: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = Dimens.gutter, vertical = 12.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filtersEnabled) {
            FilterChip(
                selected = activeFilters > 0,
                onClick = onOpenFilters,
                leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                label = {
                    Text(if (activeFilters > 0) "Фильтры ($activeFilters)" else "Фильтры")
                },
            )
        }
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
