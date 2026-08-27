package net.filmix.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.window.core.layout.WindowWidthSizeClass
import net.filmix.core.designsystem.theme.FilmixTheme
import net.filmix.feature.home.HomeRail
import net.filmix.feature.home.HomeUiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilmixTheme(darkTheme = true) {
                FilmixApp()
            }
        }
    }
}

@Composable
private fun FilmixApp() {
    var destination by remember { mutableStateOf(Destination.Home) }

    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val compact = widthClass == WindowWidthSizeClass.COMPACT

    val state = remember {
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
            Destination.Home -> net.filmix.feature.home.HomeScreen(
                state = state,
                compact = compact,
                modifier = modifier,
            )

            else -> Placeholder(destination, modifier)
        }
    }
}
