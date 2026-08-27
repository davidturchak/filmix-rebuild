package net.filmix.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.theme.LocalDimensions

/**
 * A titled horizontal strip of posters. The home screen is a vertical stack of
 * these, which is what lets the layout actually use the full tablet width
 * instead of the ~25% the original list occupied.
 */
@Composable
fun <T> Rail(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = LocalDimensions.current.gutter),
    item: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = LocalDimensions.current.gutter, bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.railGap),
            contentPadding = contentPadding,
        ) {
            items(
                count = items.size,
                key = if (key != null) { index -> key(items[index]) } else null,
            ) { index ->
                item(items[index])
            }
        }
    }
}
