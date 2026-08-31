package net.filmix.feature.detail

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import net.filmix.core.designsystem.component.isFocused
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
 * half the height of the screen it was floating over.
 *
 * The panel is [androidx.compose.material3.ColorScheme.surfaceContainer] with a
 * hairline edge and **no tonal elevation**. Material's elevation tint blends
 * `primary` into a surface, and this app's `primary` is the brand orange, so
 * elevating this panel washed the whole thing orange — against the palette's
 * own rule that the orange is an accent for actions and focus, never a ground.
 * A dark UI separates a panel from what is behind it with a hairline, not a
 * tint.
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
    val focused by interaction.isFocused()
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
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    // Not the full width: a line spanning a 960dp TV is a line
                    // nobody can track back to the start of. This lands the
                    // measure near sixty characters at the TV type scale.
                    // Height is taken as far as the screen allows, because that
                    // is only ever fewer pages to turn.
                    .fillMaxWidth(if (compact) 0.94f else 0.7f)
                    .fillMaxHeight(0.9f),
            ) {
                Column(Modifier.padding(if (compact) 20.dp else 28.dp)) {
                    // No focus ring. The design system rings chips and
                    // buttons, but a 3dp accent border around a column this
                    // large stops reading as a ring and becomes a big orange
                    // box — which is the whole panel's worth of attention spent
                    // on saying "focused" in a window holding two focusable
                    // things. The rail says it instead, by lighting up.
                    Row(Modifier.weight(1f)) {
                        ReadingRail(scroll, focused, Modifier.fillMaxHeight())
                        Spacer(Modifier.width(if (compact) 14.dp else 20.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyLarge.let {
                                // Looser leading than the app's ramp. Everywhere
                                // else body text runs a line or two; this is the
                                // only screen someone reads a paragraph on, and
                                // at three metres the eye needs the extra room
                                // to find the start of the next line.
                                it.copy(lineHeight = it.fontSize * 1.55f)
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
                                    // ScrollState's mutex makes each repeat
                                    // cancel the running animation rather than
                                    // queue behind it — which tears through the
                                    // text far faster than anyone reads. One
                                    // page at a time. Repeats landing
                                    // mid-animation are still claimed, so focus
                                    // cannot slip out to «Закрыть»
                                    // mid-paragraph; they just do not stack.
                                    if (!scroll.isScrollInProgress) {
                                        scope.launch {
                                            scroll.animateScrollTo(
                                                target,
                                                tween(SCROLL_MS),
                                            )
                                        }
                                    }
                                    true
                                }
                                .focusRequester(body)
                                // Outside the scroller, deliberately. Inside it
                                // the focus target would take the size of the
                                // scrolled *content* rather than the window.
                                .focusable(interactionSource = interaction)
                                .verticalScroll(scroll),
                        )
                    }
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

/**
 * How far through the description you are.
 *
 * A remote gives no sense of length: you press DOWN and cannot tell whether
 * two presses remain or twenty, which is the difference between reading on and
 * giving up. The rail says so at a glance, and its thumb is the one thing in
 * this window that moves — so it earns the brand orange, on the palette's own
 * terms, where the panel behind it does not.
 *
 * It carries focus too, lighting from muted to accent when the text holds it.
 * That is the palette's rule followed literally — the orange marks what is
 * live — and it costs four pixels where a ring around the column cost the
 * whole panel's composure.
 *
 * It runs down the *leading* edge rather than sitting where a scrollbar would.
 * That is the margin the eye already returns to on every line, so progress
 * stays in peripheral vision while reading instead of needing to be looked up.
 *
 * Absent when the text fits, because then there is nothing to be told.
 */
@Composable
private fun ReadingRail(
    scroll: ScrollState,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val content = scroll.maxValue + scroll.viewportSize
    if (scroll.maxValue <= 0 || scroll.viewportSize <= 0 || content <= 0) return

    BoxWithConstraints(
        modifier
            .width(RAIL_WIDTH)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        // Never a dot: past a certain length the thumb stops being readable as
        // a position and becomes a speck, so it keeps a floor and the rail
        // reads as "a long way to go" instead.
        val share = (scroll.viewportSize.toFloat() / content).coerceIn(0.1f, 1f)
        val progress = (scroll.value.toFloat() / scroll.maxValue).coerceIn(0f, 1f)
        val thumb = maxHeight * share
        Box(
            Modifier
                .offset(y = (maxHeight - thumb) * progress)
                .height(thumb)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(
                    if (focused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
        )
    }
}

/** Wide enough to read across a room, narrow enough to stay a margin rule. */
private val RAIL_WIDTH = 4.dp

/** Long enough to read as a page turn, short enough not to pace a held key. */
private const val SCROLL_MS = 180

/** Frames to keep trying for, before leaving the reader unfocused. */
private const val FOCUS_ATTEMPTS = 8
