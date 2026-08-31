package net.filmix.core.designsystem.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.filmix.core.model.PageScroll

/**
 * A block of text a remote can read.
 *
 * The D-pad scrolls by hopping between focusable nodes, so a container of
 * inert text cannot be scrolled at all: whatever does not fit the window is
 * unreachable, and the window reads as a static box. Such a container has to
 * claim UP and DOWN for itself — which is what this does, one page per press,
 * handing the press back to focus search at either end so the cursor is never
 * trapped in the text. [PageScroll] owns the arithmetic and the handoff rule,
 * where they are tested.
 *
 * Apply it *outside* the `verticalScroll` and outside the `focusable`, so the
 * focus target takes the size of the window rather than of the scrolled
 * content. Pair it with [ReadingRail]: a press that reveals more text is worth
 * nothing if there is no way to tell there was more.
 */
fun Modifier.pageOnDpad(scroll: ScrollState): Modifier = composed {
    val scope = rememberCoroutineScope()
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
        // A held key repeats every few frames, and ScrollState's mutex makes
        // each repeat cancel the running animation rather than queue behind it
        // — which tears through the text far faster than anyone reads. One page
        // at a time. Repeats landing mid-animation are still claimed, so focus
        // cannot slip out of the window mid-paragraph; they just do not stack.
        if (!scroll.isScrollInProgress) {
            scope.launch { scroll.animateScrollTo(target, tween(SCROLL_MS)) }
        }
        true
    }
}

/** True once [scroll] has been measured and has somewhere to go. */
fun ScrollState.overflows(): Boolean = viewportSize > 0 && maxValue > 0

/**
 * How far through a [pageOnDpad] window you are.
 *
 * A remote gives no sense of length: you press DOWN and cannot tell whether
 * two presses remain or twenty, which is the difference between reading on and
 * giving up. The rail says so at a glance, and its thumb is the one thing in
 * such a window that moves — so it earns the brand orange, on the palette's own
 * terms, where the panel behind it does not.
 *
 * It carries focus too, lighting from muted to accent when the text holds it.
 * That is the palette's rule followed literally — the orange marks what is
 * live — and it costs four pixels where a ring around a column that large cost
 * the whole panel's composure.
 *
 * It runs down the *leading* edge rather than sitting where a scrollbar would.
 * That is the margin the eye already returns to on every line, so progress
 * stays in peripheral vision while reading instead of needing to be looked up.
 *
 * Absent when the text fits, because then there is nothing to be told.
 */
@Composable
fun ReadingRail(
    scroll: ScrollState,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val content = scroll.maxValue + scroll.viewportSize
    if (!scroll.overflows() || content <= 0) return

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
val RAIL_WIDTH = 4.dp

/** Long enough to read as a page turn, short enough not to pace a held key. */
private const val SCROLL_MS = 180
