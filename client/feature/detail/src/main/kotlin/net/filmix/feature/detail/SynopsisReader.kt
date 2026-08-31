package net.filmix.feature.detail

import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 *
 * A plain [Dialog] rather than an `AlertDialog`, because this window's whole
 * job is holding text: Material caps an AlertDialog at 560dp wide however much
 * screen there is, and spends a fixed 24dp of padding plus a title block and a
 * button row on chrome — which on a 540dp-tall TV left a reading window barely
 * half the height of the screen it was floating over. This sizes to the screen
 * and gives every pixel it does not need for the close button to the text.
 */
@Composable
internal fun SynopsisReader(
    text: String,
    compact: Boolean,
    onClose: () -> Unit,
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

    Dialog(
        onDismissRequest = onClose,
        // Otherwise the window is capped at Material's dialog width and the
        // fractions below measure against that cap instead of the screen.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    // Not the full width: a line of text spanning a 960dp TV is
                    // a line nobody can track back to the start of. Height is
                    // taken as far as the screen allows, because that is only
                    // ever fewer pages to turn.
                    .fillMaxWidth(if (compact) 0.94f else 0.7f)
                    .fillMaxHeight(0.9f),
            ) {
                Column(Modifier.padding(READER_PADDING)) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            // All the room the close button does not need. The
                            // page step is read back off the laid-out viewport,
                            // so the reader adapts to whatever this comes to
                            // rather than paging by a guessed constant.
                            .weight(1f)
                            .fillMaxWidth()
                            // No lift: a block this wide would have its ring
                            // clipped.
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
                                // A held key repeats every few frames, and
                                // ScrollState's mutex makes each repeat cancel
                                // the running animation rather than queue behind
                                // it — which tears through the text far faster
                                // than anyone reads. One page at a time. The
                                // repeats that land mid-animation are still
                                // claimed, so focus cannot slip out to «Закрыть»
                                // mid-paragraph; they simply do not stack.
                                if (!scroll.isScrollInProgress) {
                                    scope.launch {
                                        scroll.animateScrollTo(target, tween(SCROLL_MS))
                                    }
                                }
                                true
                            }
                            .focusRequester(body)
                            // Outside the scroller, deliberately. Inside it, the
                            // focus target and its ring would take the size of
                            // the scrolled *content* rather than the window: a
                            // ring drawn around off-screen text.
                            .focusable(interactionSource = interaction)
                            .padding(RING_INSET)
                            .verticalScroll(scroll),
                    )
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

/** The window's own inset. Kept tight: the text is what the window is for. */
private val READER_PADDING = 20.dp

/** Keeps the focus ring clear of the text it surrounds. */
private val RING_INSET = 8.dp

/** Long enough to read as a page turn, short enough not to pace a held key. */
private const val SCROLL_MS = 180

/** Frames to keep trying for, before leaving the reader unfocused. */
private const val FOCUS_ATTEMPTS = 8
