package net.filmix.client

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Catalog(R.string.nav_catalog, Icons.Filled.GridView),
    Search(R.string.nav_search, Icons.Filled.Search),
    Favourites(R.string.nav_favourites, Icons.Filled.Favorite),
    History(R.string.nav_history, Icons.Filled.History),
    Profile(R.string.nav_profile, Icons.Filled.Person),
}

/**
 * Navigation adapts to width rather than always using a drawer as the original
 * app did: a bottom bar on phones, a persistent rail from medium widths up —
 * which is what the 1181dp tablet gets.
 */
@Composable
fun AppScaffold(
    current: Destination,
    compact: Boolean,
    onSelect: (Destination) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (compact) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = dest == current,
                            onClick = { onSelect(dest) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { padding ->
            content(Modifier.padding(padding).consumeWindowInsets(padding))
        }
    } else {
        // enableEdgeToEdge draws beneath the system bars, so without this the
        // top of every screen sits under the status bar — tab rows and filter
        // chips end up partly untappable.
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { dest ->
                    NavigationRailItem(
                        selected = dest == current,
                        onClick = { onSelect(dest) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Placeholder(destination: Destination, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
