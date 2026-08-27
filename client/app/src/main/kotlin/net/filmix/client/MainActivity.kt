package net.filmix.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import net.filmix.core.designsystem.theme.FilmixTheme
import net.filmix.core.model.Post
import net.filmix.core.model.VideoSource
import net.filmix.feature.detail.DetailScreen
import net.filmix.feature.detail.DetailViewModel
import net.filmix.feature.home.HomeScreen
import net.filmix.feature.home.HomeViewModel
import net.filmix.feature.profile.ProfileScreen
import net.filmix.feature.profile.ProfileViewModel

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
        LaunchedEffect(postId) { detailVm.load(postId) }
        DetailScreen(
            state = detailState,
            compact = compact,
            onBack = { openPostId = null },
            onRelatedClick = { openPostId = it.id },
            onPlay = { source ->
                detailState.post?.let { playing = it to source }
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

            Destination.Profile -> {
                val vm: ProfileViewModel = viewModel(factory = factory)
                val state by vm.state.collectAsState()
                ProfileScreen(
                    state = state,
                    modifier = modifier,
                    onStartPairing = vm::startPairing,
                    onSignOut = vm::signOut,
                )
            }

            else -> Placeholder(destination, modifier)
        }
    }
}
