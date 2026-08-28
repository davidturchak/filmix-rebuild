package net.filmix.client

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.filmix.core.data.PlaybackRepository
import net.filmix.core.data.PlaybackRequest
import net.filmix.core.data.SettingsStore
import net.filmix.core.model.Post
import net.filmix.core.model.VideoSource
import net.filmix.core.model.externalProgressFromExtras
import net.filmix.feature.player.NoSourceMessage
import net.filmix.feature.player.PlayerScreen

/**
 * Bridges a chosen [VideoSource] to the player: resolves the concrete URL and
 * resume position off the main thread, then hands both to [PlayerScreen] — or
 * to the external player chosen in Config — and persists progress on the way
 * back.
 *
 * Kept in :app rather than :feature:player so the player composable stays a
 * pure view over a URL, with no repository dependency.
 */
@Composable
fun PlaybackHost(
    post: Post,
    source: VideoSource,
    repository: PlaybackRepository,
    settings: SettingsStore,
    /**
     * Deliberately not a composition scope. The player reports its final
     * position as it is torn down, which happens in the same pass that removes
     * this composable — a `rememberCoroutineScope` is cancelled by then, so that
     * write never ran and a user who paused, seeked and left kept their old
     * resume point.
     */
    saveScope: CoroutineScope,
    modifier: Modifier = Modifier,
    /** Series coordinates for history reporting; "0" for films, like the reference app. */
    season: String = "0",
    episode: String = "0",
    /** Invoked once the server has accepted the watch report. */
    onWatched: () -> Unit = {},
    onExit: () -> Unit,
) {
    var request by remember(source) { mutableStateOf<PlaybackRequest?>(null) }
    var unavailable by remember(source) { mutableStateOf(false) }
    var external by remember(source) { mutableStateOf(false) }
    val context = LocalContext.current

    // The result callback is what makes the external path more than a bare
    // ACTION_VIEW: MX-compatible players and VLC report the position reached,
    // which feeds the same resume store the built-in player writes. If the
    // process dies while the external app is foregrounded this composition is
    // gone and one resume update is lost — the built-in player does not
    // survive that either, so it is not worth promoting `playing` to saveable.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val active = request
        if (active != null) {
            externalProgressFromExtras(result.data?.extras.toExtrasMap())?.let { progress ->
                saveScope.launch {
                    repository.saveProgress(active, progress.positionMs, progress.durationMs)
                }
            }
        }
        onExit()
    }

    LaunchedEffect(post.id, source) {
        val prepared = repository.prepare(post.id, post.title, source)
        if (prepared == null) {
            unavailable = true
            return@LaunchedEffect
        }
        // Fire-and-forget: history reporting must not gate playback.
        saveScope.launch {
            if (repository.reportWatched(prepared, season, episode)) onWatched()
        }

        val pkg = settings.externalPlayerPackage()
        if (pkg != null) {
            if (!ExternalPlayers.isInstalled(context, pkg)) {
                // Uninstalled since it was chosen: heal the setting so Config
                // agrees with what actually plays, and fall back to built-in.
                saveScope.launch { settings.setExternalPlayerPackage(null) }
            } else {
                val launched = runCatching {
                    launcher.launch(
                        ExternalPlayers.playbackIntent(
                            packageName = pkg,
                            url = prepared.streamUrl,
                            title = prepared.title,
                            startPositionMs = prepared.startPositionMs,
                        ),
                    )
                }.isSuccess // isInstalled raced an uninstall — play built-in.
                if (launched) {
                    // Kept alive for the result callback's saveProgress.
                    request = prepared
                    external = true
                    return@LaunchedEffect
                }
            }
        }
        request = prepared
    }

    when {
        unavailable -> NoSourceMessage(modifier)
        external -> ExternalPlaybackMessage(modifier, onExit)
        request != null -> {
            val active = request!!
            PlayerScreen(
                streamUrl = active.streamUrl,
                title = active.title,
                startPositionMs = active.startPositionMs,
                modifier = modifier,
                onProgress = { position, duration ->
                    saveScope.launch { repository.saveProgress(active, position, duration) }
                },
                onBack = onExit,
            )
        }
        // Nothing rendered while resolving: it is a single DataStore read plus
        // a Room lookup, too brief to justify a spinner flash.
    }
}

/**
 * Shown behind the external player. The BackHandler is the escape hatch for a
 * player that finishes without delivering our activity result.
 */
@Composable
private fun ExternalPlaybackMessage(modifier: Modifier = Modifier, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Воспроизведение во внешнем плеере…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Bundle?.toExtrasMap(): Map<String, Any?> {
    if (this == null) return emptyMap()
    // The untyped get is the point: which type each player wrote is unknown,
    // and the tested decoder sorts that out.
    @Suppress("DEPRECATION")
    return keySet().associateWith { get(it) }
}
