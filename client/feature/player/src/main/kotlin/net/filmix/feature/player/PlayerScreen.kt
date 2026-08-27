package net.filmix.feature.player

import android.app.Activity
import android.view.View
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

    // Held rather than built inline, so focus can be handed to it. On a remote
    // that is the whole game: PlayerView reveals its controls from its own
    // dispatchKeyEvent, so a press only counts if this view has Android focus.
    // Without it the screen answered no key at all — not CENTRE, not the D-pad,
    // not even MEDIA_PLAY_PAUSE — and BACK was the only way out.
    val playerView = remember {
        PlayerView(context).apply {
            this.player = player
            useController = true
            setShowNextButton(false)
            setShowPreviousButton(false)
            isFocusable = true
            isFocusableInTouchMode = true
            // The controls auto-show at the start of playback and take focus
            // for their buttons; when the timeout hides them again, focus would
            // go with them and leave the window with nothing focused — which is
            // why the controls went missing partway through rather than at
            // once. Take it back each time they close.
            setControllerVisibilityListener(
                PlayerView.ControllerVisibilityListener { visibility ->
                    if (visibility != View.VISIBLE) requestFocus()
                },
            )
        }
    }

    LaunchedEffect(playerView) {
        // A frame, so the view is attached and can actually hold focus.
        withFrameNanos { }
        playerView.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // The backstop. Handing the view focus above is what makes the
            // controls navigable once they are up, but whether a press reaches
            // it depends on who holds focus at that instant — and the state
            // where nothing did is the bug. Compose is in the dispatch path
            // either way, so claim the first press here and summon the controls
            // outright; once they are visible their own buttons hold focus and
            // handle everything, so the event is passed through untouched.
            .onPreviewKeyEvent { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key !in SummonControlKeys -> false
                    playerView.isControllerFullyVisible -> false
                    else -> {
                        playerView.showController()
                        true
                    }
                }
            },
    ) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The only keys the summoning backstop above may claim.
 *
 * It used to claim everything except Back, which is a wider net than it looks:
 * a key event reaches the view hierarchy *before* the window handles it, so
 * consuming one here stops the window ever seeing it. That ate the volume keys —
 * the first press only flashed the controls instead of changing the volume — and
 * swallowed the first MEDIA_PLAY_PAUSE, which is precisely the key that should
 * work without looking at the screen. Anything not listed here now passes
 * through to the view, which shows the controls itself once it has acted on it.
 */
private val SummonControlKeys = setOf(
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
)

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
