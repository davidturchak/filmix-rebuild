package net.filmix.feature.detail

import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.rememberFocusInteraction
import net.filmix.core.model.PageScroll

/**
 * The full description, raised over the detail screen.
 *
 * The description is clamped on the page because a remote cannot read a
 * paragraph in a LazyColumn: the D-pad scrolls that list by hopping between
 * focusable nodes, so an inert block of text goes past in one jump and can
 * never be paged back to. Here the text has a container of its own, which it
 * can scroll because it takes UP and DOWN for itself.
 */
@Composable
internal fun SynopsisReader(
    title: String,
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val interaction = rememberFocusInteraction()
    val body = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // A dialog arrives with nothing focused, which on a remote reads as a
    // frozen screen. The body takes it rather than «Закрыть», so the first
    // DOWN pages the text instead of skipping it — the whole point of opening
    // this. Retried per frame and never allowed to throw, the same way
    // UpdatePrompt and the detail screen's play button claim focus: the
    // requester is attached a frame or more after this effect first runs.
    LaunchedEffect(text) {
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { body.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = READER_MAX_HEIGHT)
                    // No lift: a block this wide would have its ring clipped.
                    .focusRing(
                        shape = MaterialTheme.shapes.small,
                        scaleWhenFocused = 1f,
                        interactionSource = interaction,
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        val forward = when (event.key) {
                            Key.DirectionDown -> true
                            Key.DirectionUp -> false
                            else -> return@onPreviewKeyEvent false
                        }
                        val target = PageScroll.target(
                            value = scroll.value,
                            maxValue = scroll.maxValue,
                            viewport = scroll.viewportSize,
                            forward = forward,
                        ) ?: return@onPreviewKeyEvent false
                        // A held key repeats every few frames, and ScrollState's
                        // mutex makes each repeat cancel the running animation
                        // rather than queue behind it — which tears through the
                        // text far faster than anyone reads. One page at a time.
                        // The repeats that land mid-animation are still claimed,
                        // so focus cannot slip out to «Закрыть» mid-paragraph;
                        // they simply do not stack.
                        if (!scroll.isScrollInProgress) {
                            scope.launch {
                                scroll.animateScrollTo(target, tween(SCROLL_MS))
                            }
                        }
                        true
                    }
                    .focusRequester(body)
                    // Outside the scroller, deliberately. Inside it, the focus
                    // target and its ring would take the size of the scrolled
                    // *content* rather than the window: a ring drawn around
                    // off-screen text.
                    .focusable(interactionSource = interaction)
                    .padding(RING_INSET)
                    .verticalScroll(scroll),
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        },
    )
}

/**
 * How tall the text may get before it scrolls inside itself.
 *
 * Sized for the 540dp-tall TV this app is built for, where the dialog's title,
 * button and padding leave a little under 400dp: past that the reader pushes
 * its own close button off the screen. A file constant rather than a
 * [net.filmix.core.designsystem.theme.Dimensions] field — that class is for
 * metrics the same element needs a different value of per device class, and
 * this cap is the same everywhere and read in one place.
 */
private val READER_MAX_HEIGHT = 300.dp

/** Keeps the focus ring clear of the text it surrounds. */
private val RING_INSET = 12.dp

/** Long enough to read as a page turn, short enough not to pace a held key. */
private const val SCROLL_MS = 180

/** Frames to keep trying for, before leaving the reader unfocused. */
private const val FOCUS_ATTEMPTS = 8
