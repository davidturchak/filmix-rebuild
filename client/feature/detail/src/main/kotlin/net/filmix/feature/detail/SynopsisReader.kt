package net.filmix.feature.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.filmix.core.designsystem.component.ReadingRail
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.designsystem.component.isFocused
import net.filmix.core.designsystem.component.pageOnDpad
import net.filmix.core.designsystem.component.rememberFocusInteraction

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
    val close = remember { FocusRequester() }
    val focused by interaction.isFocused()
    
    // A dialog arrives with nothing focused, which on a remote reads as a
    // frozen screen.
    LaunchedEffect(text) {
        // Wait for the first layout. Until the text is measured there is no
        // telling whether it scrolls, and that is what decides who should hold
        // the cursor.
        var frames = 0
        while (scroll.viewportSize == 0 && frames < FOCUS_ATTEMPTS) {
            withFrameNanos { }
            frames++
        }
        // Text that scrolls keeps the cursor on the body, so the first DOWN
        // pages it instead of skipping to the button — the whole point of
        // opening this. Text that fits has nothing to page and no rail to light
        // up, and since this window draws no ring around the body, leaving the
        // cursor there would open it with nothing marked at all. «Закрыть»
        // rings, so it takes the cursor instead.
        //
        // An unmeasured scroll state still reports maxValue = Int.MAX_VALUE, so
        // giving up on the wait falls through to the body — the safe half.
        val target = if (scroll.maxValue > 0) body else close
        // Retried per frame and never allowed to throw, the same way
        // UpdatePrompt and the detail screen's play button claim focus: the
        // requester is attached a frame or more after this effect first runs.
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
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
                    //
                    // The gap is the Row's arrangement rather than a Spacer,
                    // because the rail is absent whenever the text fits — which
                    // is most of the time, since a synopsis only has to overrun
                    // four lines on the page to open this near-full-screen
                    // window. A Spacer would leave that gap behind as an
                    // unexplained indent with nothing in it.
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (compact) 14.dp else 20.dp,
                        ),
                    ) {
                        ReadingRail(scroll, focused, Modifier.fillMaxHeight())
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
                                // Claims UP and DOWN so the paragraph turns
                                // pages, and hands them back at either end so
                                // «Закрыть» stays reachable.
                                .pageOnDpad(scroll)
                                .focusRequester(body)
                                // Outside the scroller, deliberately. Inside it
                                // the focus target would take the size of the
                                // scrolled *content* rather than the window.
                                .focusable(interactionSource = interaction)
                                .verticalScroll(scroll),
                        )
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.End)
                            .focusRequester(close),
                    ) {
                        Text("Закрыть")
                    }
                }
            }
        }
    }
}

/** Frames to keep trying for, before leaving the reader unfocused. */
private const val FOCUS_ATTEMPTS = 8
