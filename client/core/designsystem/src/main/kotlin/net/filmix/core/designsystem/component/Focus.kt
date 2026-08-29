package net.filmix.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * D-pad focus indication.
 *
 * Compose already moves focus correctly with a remote, but nothing renders it,
 * which makes the app unusable on a TV — you cannot see where you are. This
 * draws an accent ring and lifts the element slightly, the convention every
 * 10-foot UI uses.
 *
 * Touch is unaffected: focus is only taken by D-pad traversal, so phones and
 * tablets see no change.
 */
fun Modifier.focusRing(
    shape: Shape? = null,
    scaleWhenFocused: Float = 1.06f,
    interactionSource: MutableInteractionSource? = null,
    // The accent ring vanishes on an accent-filled surface — a primary Button
    // focused looks exactly like one that is not. Such callers pass onPrimary.
    color: Color? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val ringShape = shape ?: MaterialTheme.shapes.medium
    val ringColor = color ?: MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleWhenFocused else 1f,
        label = "focusScale",
    )
    this
        .scale(scale)
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) ringColor else Color.Transparent,
            shape = ringShape,
        )
}

/**
 * Shared interaction source so a caller can drive both [focusRing] and a
 * `clickable` from the same focus state.
 */
@Composable
fun rememberFocusInteraction(): MutableInteractionSource =
    remember { MutableInteractionSource() }

@Composable
fun MutableInteractionSource.isFocused(): State<Boolean> = collectIsFocusedAsState()

/** Keeps a focused item clear of the screen edge when a rail scrolls it in. */
fun Modifier.focusPadding(): Modifier = padding(4.dp)

/**
 * Makes D-pad entry into this container land on a chosen child instead of
 * whichever node is geometrically nearest — the rail pins entry to the current
 * tab, the episode grid to the current episode. Wraps a fragile ordering: the
 * focusProperties must precede the focusTarget that focusGroup adds, or it
 * configures the children instead of the group and does nothing at all.
 *
 * [target] is read at entry time, so it may resolve against current state; the
 * requester it returns must be attached to a composed child.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.enterFocusAt(target: () -> FocusRequester): Modifier =
    this
        .focusProperties { enter = { target() } }
        .focusGroup()
