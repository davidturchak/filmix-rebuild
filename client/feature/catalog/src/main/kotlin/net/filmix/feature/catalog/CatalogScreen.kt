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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import net.filmix.core.designsystem.component.FocusChip
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.designsystem.component.rememberFocusReturn
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.model.Post
import net.filmix.core.model.CatalogFilter
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.SortDirection
import net.filmix.core.model.SortOrder

@Composable
fun CatalogScreen(
    items: LazyPagingItems<Post>,
    /**
     * Bumped for each reload asked for behind a populated grid. Such a reload
     * keeps the grid on screen while its first page is in flight, where a
     * sort change shows the spinner.
     */
    reloads: Int = 0,
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
    val gridState = rememberLazyGridState()
    // The grid is paged, so the restored offset can point into a page that has
    // not replayed yet; nudging the item into view gives the requester
    // something to attach to.
    val focusReturn = rememberFocusReturn { key ->
        val index = key.toIntOrNull() ?: return@rememberFocusReturn
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            gridState.scrollToItem(index)
        }
    }
    // True from a stale reload being asked for until its first page lands
    // or fails. The grid stays up throughout: dropping it for the spinner
    // would also drop the cursor, and the whole point of the reload is that
    // the user did not ask for anything.
    var reloading by remember { mutableStateOf(false) }
    LaunchedEffect(reloads) {
        if (reloads > 0) reloading = true
    }
    LaunchedEffect(items.loadState.refresh) {
        val refresh = items.loadState.refresh
        if (!reloading || refresh is LoadState.Loading) return@LaunchedEffect
        reloading = false
        // The reload starts over from page one, so an index deep in the old
        // list means nothing in the new one — and the card that held the
        // cursor may not exist any more. Show the top, where what is new
        // is, and hand the cursor to the first card. Near the top the cards
        // simply take their new contents under a cursor that never moved.
        if (refresh is LoadState.NotLoading && gridState.firstVisibleItemIndex > 0 && items.itemCount > 0) {
            gridState.scrollToItem(0)
            focusReturn.restore(0)
        }
    }
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
            // While a reload asked for behind a populated grid is in flight,
            // the presenter still holds the old pages: the grid keeps showing
            // them, and a failure leaves them there — stale beats blank for a
            // load the user did not ask for. A sort change is not a reload,
            // and shows the spinner and the error as it always has.
            val inPlace = reloading && items.itemCount > 0
            when {
                items.loadState.refresh is LoadState.Loading && !inPlace ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                // Error must be checked before "no results": a failed load also
                // has an item count of zero.
                items.loadState.refresh is LoadState.Error && !inPlace -> Text(
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
                    state = gridState,
                    columns = GridCells.Adaptive(
                        minSize = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                    ),
                    contentPadding = PaddingValues(LocalDimensions.current.gutter),
                    horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.railGap),
                    verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
                ) {
                    items(count = items.itemCount) { index ->
                        val post = items[index] ?: return@items
                        PosterCard(
                            modifier = focusReturn.modifier(index),
                            title = post.title,
                            posterUrl = post.posterUrl,
                            rating = post.rating,
                            subtitle = post.lastEpisode?.label
                                ?: post.year.takeIf { it > 0 }?.toString(),
                            width = if (compact) {
                                LocalDimensions.current.posterWidthCompact
                            } else {
                                LocalDimensions.current.posterWidth
                            },
                            height = if (compact) {
                                LocalDimensions.current.posterHeightCompact
                            } else {
                                LocalDimensions.current.posterHeight
                            },
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
        // The gutter goes *inside* the scrollable (like Rail's contentPadding):
        // a scroll container clips at its own bounds, so padding outside it
        // would slice the first chip's focus ring off at the left edge.
        Modifier
            .padding(vertical = 12.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = LocalDimensions.current.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (filtersEnabled) {
            FocusChip(
                selected = activeFilters > 0,
                onClick = onOpenFilters,
                leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) },
                label = if (activeFilters > 0) "Фильтры ($activeFilters)" else "Фильтры",
            )
        }
        SortOrder.entries.forEach { option ->
            FocusChip(
                selected = option == sort,
                onClick = { onSortChange(option) },
                label = option.label,
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
