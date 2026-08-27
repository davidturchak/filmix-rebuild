package net.filmix.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.core.layout.WindowWidthSizeClass
import net.filmix.core.designsystem.theme.FilmixTheme
import net.filmix.core.model.Post
import net.filmix.core.model.VideoSource
import net.filmix.feature.catalog.CatalogScreen
import net.filmix.feature.catalog.CatalogViewModel
import net.filmix.feature.detail.DetailScreen
import net.filmix.feature.detail.DetailViewModel
import net.filmix.feature.home.HomeScreen
import net.filmix.feature.home.HomeViewModel
import net.filmix.feature.library.HistoryScreen
import net.filmix.feature.library.HistoryViewModel
import net.filmix.feature.library.LibraryScreen
import net.filmix.feature.library.LibraryViewModel
import net.filmix.feature.profile.ProfileScreen
import net.filmix.feature.profile.ProfileViewModel
import net.filmix.feature.search.SearchScreen
import net.filmix.feature.search.SearchViewModel

class MainActivity : ComponentActivity() {

    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        graph = AppGraph(this)
        enableEdgeToEdge()
        setContent {
            FilmixTheme(darkTheme = true) {
                FilmixApp(graph)
            }
        }
    }
}

@Composable
private fun FilmixApp(graph: AppGraph) {
    // rememberSaveable so the tab survives activity recreation (rotation,
    // process death restore) rather than snapping back to Home.
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }

    // Detail is an overlay rather than a tab: it covers whichever tab the user
    // came from, so back returns them there with that tab's state intact.
    // Navigation-Compose takes this over when search and the player land.
    var openPostId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Playback sits above detail. Held as objects rather than ids because the
    // source list only exists on the loaded post.
    var playing by remember { mutableStateOf<Pair<Post, VideoSource>?>(null) }

    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val compact = widthClass == WindowWidthSizeClass.COMPACT
    val factory = remember(graph) { GraphViewModelFactory(graph) }

    val active = playing
    if (active != null) {
        PlaybackHost(
            post = active.first,
            source = active.second,
            repository = graph.playbackRepository,
            onExit = { playing = null },
        )
        return
    }

    val postId = openPostId
    if (postId != null) {
        BackHandler { openPostId = null }
        val detailVm: DetailViewModel = viewModel(factory = factory)
        val detailState by detailVm.state.collectAsState()
        val selection by detailVm.selection.collectAsState()
        LaunchedEffect(postId) { detailVm.load(postId) }
        DetailScreen(
            state = detailState,
            compact = compact,
            // Detail renders outside AppScaffold, so it needs its own insets —
            // without them the season chips sit under the status bar and the
            // top row of the picker swallows taps.
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            onBack = { openPostId = null },
            onRelatedClick = { openPostId = it.id },
            onPlay = { source ->
                detailState.post?.let { playing = it to source }
            },
            onToggleFavourite = detailVm::toggleFavourite,
            onToggleWatchLater = detailVm::toggleWatchLater,
            selection = selection,
            onSelectSeason = detailVm::selectSeason,
            onSelectTranslation = detailVm::selectTranslation,
            onPlayEpisode = { episode ->
                detailState.post?.let { playing = it to episode.source }
            },
        )
        return
    }

    AppScaffold(
        current = destination,
        compact = compact,
        onSelect = { destination = it },
    ) { modifier ->
        when (destination) {
            Destination.Home -> {
                val vm: HomeViewModel = viewModel(factory = factory)
                val homeState by vm.state.collectAsState()
                HomeScreen(
                    state = homeState,
                    compact = compact,
                    modifier = modifier,
                    onRetry = vm::refresh,
                    onPostClick = { openPostId = it.id },
                    onPlayClick = { openPostId = it.id },
                )
            }

            Destination.Catalog -> {
                val vm: CatalogViewModel = viewModel(factory = factory)
                val sort by vm.sort.collectAsState()
                val filterOptions by vm.filterOptions.collectAsState()
                val items = vm.items.collectAsLazyPagingItems()
                CatalogScreen(
                    items = items,
                    sort = sort.order,
                    direction = sort.direction,
                    filter = sort.filter,
                    filterOptions = filterOptions,
                    compact = compact,
                    modifier = modifier,
                    onSortChange = vm::setSort,
                    onDirectionToggle = vm::toggleDirection,
                    onFilterChange = vm::setFilter,
                    onClearFilter = vm::clearFilter,
                    onPostClick = { openPostId = it.id },
                )
            }

            Destination.Search -> {
                val vm: SearchViewModel = viewModel(factory = factory)
                val q by vm.query.collectAsState()
                val suggestions by vm.suggestions.collectAsState()
                val results = vm.results.collectAsLazyPagingItems()
                SearchScreen(
                    query = q,
                    suggestions = suggestions,
                    results = results,
                    compact = compact,
                    modifier = modifier,
                    onQueryChange = vm::onQueryChange,
                    onSubmit = vm::submit,
                    onClear = vm::clear,
                    onPostClick = { openPostId = it.id },
                )
            }

            Destination.Favourites -> {
                val vm: LibraryViewModel = viewModel(factory = factory)
                val libState by vm.state.collectAsState()
                LibraryScreen(
                    state = libState,
                    compact = compact,
                    modifier = modifier,
                    onTabChange = vm::selectTab,
                    onPostClick = { openPostId = it.id },
                )
            }

            Destination.History -> {
                val vm: HistoryViewModel = viewModel(factory = factory)
                val historyState by vm.state.collectAsState()
                HistoryScreen(
                    state = historyState,
                    compact = compact,
                    modifier = modifier,
                    onPostClick = { openPostId = it.id },
                    onRemove = vm::remove,
                    onClearAll = vm::clearAll,
                )
            }

            Destination.Profile -> {
                val vm: ProfileViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsState()
                val quality by vm.preferredQuality.collectAsState()
                ProfileScreen(
                    state = state,
                    modifier = modifier,
                    onStartPairing = vm::startPairing,
                    onSignOut = vm::signOut,
                    preferredQuality = quality,
                    onQualityChange = vm::setPreferredQuality,
                    version = vm.version,
                )
            }

            else -> Placeholder(destination, modifier)
        }
    }
}
