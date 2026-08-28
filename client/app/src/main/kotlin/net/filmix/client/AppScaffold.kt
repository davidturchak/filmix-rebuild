package net.filmix.client

import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.isFocused
import net.filmix.core.designsystem.component.rememberFocusInteraction
import net.filmix.core.designsystem.theme.LocalIsTv

/**
 * Top inset for the rail's focus ring — see the call site. Kept out of the
 * device-class Dimensions because it corrects a Material layout quirk rather
 * than scaling with the viewing distance.
 */
private val RailFocusRingInset = 8.dp

enum class Destination(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Catalog(R.string.nav_catalog, Icons.Filled.GridView),
    Search(R.string.nav_search, Icons.Filled.Search),
    Favourites(R.string.nav_favourites, Icons.Filled.Favorite),
    History(R.string.nav_history, Icons.Filled.History),
    Profile(R.string.nav_profile, Icons.Filled.Person),
    Config(R.string.nav_config, Icons.Filled.Settings),
}

/**
 * Navigation adapts to width rather than always using a drawer as the original
 * app did: a bottom bar on phones, a persistent rail from medium widths up —
 * which is what the 1181dp tablet gets.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppScaffold(
    current: Destination,
    compact: Boolean,
    onSelect: (Destination) -> Unit,
    /**
     * Bumped by the caller whenever [current] changed from somewhere other than
     * the rail itself — BACK, in practice. On TV focus *is* the selection, so
     * otherwise the pill moves to Home while the ring stays on the tab the user
     * left, and the next press steps from the wrong place.
     */
    railFocusTick: Int = 0,
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
        val isTv = LocalIsTv.current
        val requesters = remember { Destination.entries.associateWith { FocusRequester() } }
        // Only on a tick, never on `current` alone: selecting by focus already
        // put the ring where it belongs, and re-requesting on every change would
        // yank focus out of the content the user is in.
        LaunchedEffect(railFocusTick) {
            if (railFocusTick == 0) return@LaunchedEffect
            withFrameNanos { }
            runCatching { requesters.getValue(current).requestFocus() }
        }

        // enableEdgeToEdge draws beneath the system bars, so without this the
        // top of every screen sits under the status bar — tab rows and filter
        // chips end up partly untappable.
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    // Coming back out of the content with LEFT has to land on
                    // the tab you are already on. Plain focus search picks
                    // whichever item is nearest vertically instead — LEFT from
                    // the third row of the catalog grid lands on Profile — and
                    // because focus selects on TV that silently switched tabs.
                    // Order matters: focusProperties has to precede the
                    // focusTarget that focusGroup adds, or it configures the
                    // items instead of the group and does nothing.
                    .focusProperties { enter = { requesters.getValue(current) } }
                    .focusGroup(),
            ) {
                Destination.entries.forEach { dest ->
                    val interaction = rememberFocusInteraction()
                    val focused by interaction.isFocused()
                    // A remote has no cheap "commit" gesture, so landing on a
                    // tab opens it: D-pad down from Home shows Catalog without
                    // a separate centre press. Touch keeps click-to-select,
                    // because a pointer never focuses anything.
                    if (isTv) {
                        LaunchedEffect(focused) { if (focused) onSelect(dest) }
                    }
                    NavigationRailItem(
                        selected = dest == current,
                        onClick = { onSelect(dest) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                        interactionSource = interaction,
                        modifier = Modifier
                            .focusRequester(requesters.getValue(dest))
                            // No lift: the item is as wide as the rail, so a
                            // scaled ring would be clipped at both edges.
                            .focusRing(scaleWhenFocused = 1f, interactionSource = interaction)
                            // Material places the selected pill flush with the
                            // item's top edge, while the label's line height
                            // leaves ~5dp of slack underneath. A ring on the
                            // raw bounds therefore sits directly on the pill
                            // and looks generous under the label; padding the
                            // top back inside the ring evens the two up.
                            .padding(top = RailFocusRingInset),
                    )
                }
            }
            content(Modifier.fillMaxSize())
        }
    }
}
