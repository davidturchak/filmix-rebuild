package net.filmix.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import net.filmix.core.designsystem.theme.FilmixTheme
import net.filmix.feature.home.HomeRail
import net.filmix.feature.home.HomeScreen
import net.filmix.feature.home.HomeUiState
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

    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val compact = widthClass == WindowWidthSizeClass.COMPACT
    val factory = remember(graph) { GraphViewModelFactory(graph) }

    val homeState = remember {
        HomeUiState(
            featured = MockData.featured,
            rails = listOf(
                HomeRail("Новинки", MockData.newest),
                HomeRail("Популярное", MockData.popular),
                HomeRail("Сериалы", MockData.series),
            ),
        )
    }

    AppScaffold(
        current = destination,
        compact = compact,
        onSelect = { destination = it },
    ) { modifier ->
        when (destination) {
            Destination.Home -> HomeScreen(
                state = homeState,
                compact = compact,
                modifier = modifier,
            )

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
