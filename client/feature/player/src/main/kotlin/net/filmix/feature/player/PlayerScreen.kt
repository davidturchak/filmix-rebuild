package net.filmix.feature.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Media3 playback, replacing the reference app's `VideoView`/`MediaPlayer`.
 *
 * The screen owns the player instance and reports position back through
 * [onProgress] so the caller can persist a resume point without this composable
 * knowing about storage.
 */
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0,
    onProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            if (startPositionMs > 0) seekTo(startPositionMs)
            prepare()
            playWhenReady = true
        }
    }

    // Playback is landscape, full-bleed: hide the system bars while here and
    // restore them on the way out.
    val activity = context as? Activity
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Pause when the app goes to the background rather than playing on blindly.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Report progress periodically and once more on teardown, so a position is
    // saved whether the user backs out or the screen is destroyed.
    LaunchedEffect(player) {
        while (true) {
            kotlinx.coroutines.delay(PROGRESS_INTERVAL_MS)
            if (player.isPlaying) {
                onProgress(player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            onProgress(player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
        }
    }

    BackHandler { onBack() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val PROGRESS_INTERVAL_MS = 5_000L

/** Shown when a title has no playable source at all. */
@Composable
fun NoSourceMessage(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Нет доступных источников",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Exposed so callers can react to playback ending. */
fun Player.isEnded(): Boolean = playbackState == Player.STATE_ENDED
