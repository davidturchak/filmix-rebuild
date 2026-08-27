package net.filmix.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.model.Post

data class HistoryUiState(
    val items: List<Post> = emptyList(),
    val loading: Boolean = false,
    val signedIn: Boolean = true,
    val failed: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onPostClick: (Post) -> Unit = {},
    onRemove: (Post) -> Unit = {},
    onRetry: () -> Unit = {},
    onClearAll: () -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimensions.current.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "История просмотров",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (state.items.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                    Text("Очистить", Modifier.padding(start = 6.dp))
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                // Like the other personal lists, history returns [] when
                // unpaired, so the pairing state decides the message.
                !state.signedIn -> Message(
                    "Войдите в аккаунт на вкладке «Профиль», чтобы видеть историю просмотров.",
                )

                // Before "empty", for the same reason as the library screen.
                state.failed -> Retry("Не удалось загрузить историю", onRetry)

                state.items.isEmpty() -> Message("История пуста")

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(
                        minSize = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                    ),
                    contentPadding = PaddingValues(LocalDimensions.current.gutter),
                    horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.railGap),
                    verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
                ) {
                    items(count = state.items.size, key = { state.items[it].id }) { index ->
                        val post = state.items[index]
                        PosterCard(
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
                            onClick = { onPostClick(post) },
                            // Long-press removes one entry, mirroring the
                            // context menu the original app offered per item.
                            onLongClick = { onRemove(post) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все записи будут удалены. Действие необратимо.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearAll()
                    },
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            },
        )
    }
}
