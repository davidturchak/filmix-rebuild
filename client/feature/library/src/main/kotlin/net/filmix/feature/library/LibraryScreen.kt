package net.filmix.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.model.Post

enum class LibraryTab(val label: String) {
    Favourites("Избранное"),
    WatchLater("Смотреть позже"),
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Favourites,
    val favourites: List<Post> = emptyList(),
    val watchLater: List<Post> = emptyList(),
    val loading: Boolean = false,
    val paired: Boolean = true,
) {
    val visible: List<Post>
        get() = if (tab == LibraryTab.Favourites) favourites else watchLater
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onTabChange: (LibraryTab) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { onTabChange(tab) },
                    text = { Text(tab.label) },
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                // These endpoints return [] rather than an error when unpaired,
                // so the distinction has to come from the pairing state.
                !state.paired -> Message(
                    "Войдите в аккаунт на вкладке «Профиль», чтобы синхронизировать списки.",
                )

                state.visible.isEmpty() -> Message(
                    if (state.tab == LibraryTab.Favourites) {
                        "В избранном пока пусто"
                    } else {
                        "Список «Смотреть позже» пуст"
                    },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(
                        minSize = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                    ),
                    contentPadding = PaddingValues(LocalDimensions.current.gutter),
                    horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.railGap),
                    verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
                ) {
                    items(count = state.visible.size) { index ->
                        val post = state.visible[index]
                        PosterCard(
                            title = post.title,
                            posterUrl = post.posterUrl,
                            rating = post.rating,
                            subtitle = post.lastEpisode?.label
                                ?: post.year.takeIf { it > 0 }?.toString(),
                            width = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                            height = if (compact) {
                                LocalDimensions.current.posterHeightCompact
                            } else {
                                LocalDimensions.current.posterHeight
                            },
                            onClick = { onPostClick(post) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 48.dp),
    )
}
