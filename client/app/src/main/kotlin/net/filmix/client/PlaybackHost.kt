package net.filmix.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import net.filmix.core.data.PlaybackRepository
import net.filmix.core.data.PlaybackRequest
import net.filmix.core.model.Post
import net.filmix.core.model.VideoSource
import net.filmix.feature.player.NoSourceMessage
import net.filmix.feature.player.PlayerScreen

/**
 * Bridges a chosen [VideoSource] to the player: resolves the concrete URL and
 * resume position off the main thread, then hands both to [PlayerScreen] and
 * persists progress on the way back.
 *
 * Kept in :app rather than :feature:player so the player composable stays a
 * pure view over a URL, with no repository dependency.
 */
@Composable
fun PlaybackHost(
    post: Post,
    source: VideoSource,
    repository: PlaybackRepository,
    modifier: Modifier = Modifier,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var request by remember(source) { mutableStateOf<PlaybackRequest?>(null) }
    var unavailable by remember(source) { mutableStateOf(false) }

    LaunchedEffect(post.id, source) {
        val prepared = repository.prepare(post.id, post.title, source)
        if (prepared == null) {
            unavailable = true
        } else {
            request = prepared
            // Fire-and-forget: history reporting must not gate playback.
            scope.launch { repository.reportWatched(prepared) }
        }
    }

    when {
        unavailable -> NoSourceMessage(modifier)
        request != null -> {
            val active = request!!
            PlayerScreen(
                streamUrl = active.streamUrl,
                title = active.title,
                startPositionMs = active.startPositionMs,
                modifier = modifier,
                onProgress = { position, duration ->
                    scope.launch { repository.saveProgress(active, position, duration) }
                },
                onBack = onExit,
            )
        }
        // Nothing rendered while resolving: it is a single DataStore read plus
        // a Room lookup, too brief to justify a spinner flash.
    }
}
