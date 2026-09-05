package net.filmix.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import net.filmix.core.designsystem.theme.LocalIsTv

/** Frames to wait for the item to turn up on its own, before scrolling to it. */
private const val FramesBeforeScroll = 8

/** How long to keep trying after that, before giving up on it entirely. */
private const val RestoreFrames = 120

/**
 * Puts focus back on the card the user opened when they come back to a list.
 *
 * A tab's content leaves composition entirely while a detail screen is open, and
 * Compose drops focus when the focused node goes away. So on a TV you return to
 * a list with *nothing* focused, and the next D-pad press jumps to whatever
 * happens to be first — the remote feels like it lost the cursor. The saveable
 * state holder around the tabs restores the scroll offset; this restores the
 * cursor within it.
 *
 * Touch is unaffected: there is nothing to restore when nothing was focused.
 */
class FocusReturn internal constructor(
    private val pending: MutableState<String>,
    private val restores: MutableState<Int>,
    internal val requester: FocusRequester,
    internal val enabled: Boolean,
) {
    /**
     * Attach to every item. Only the one being returned to claims the requester,
     * so exactly one node holds it — which means [id] has to be unique across
     * the whole screen, not just within one list. A post id alone is not: the
     * home screen's rails routinely carry the same film in two of them, and two
     * holders send focus, and the scroll that follows it, to the wrong rail.
     */
    fun modifier(id: Any): Modifier =
        if (enabled && pending.value == id.toString()) {
            Modifier.focusRequester(requester)
        } else {
            Modifier
        }

    /** Call from the item's click handler, before navigating away. */
    fun opened(id: Any) {
        pending.value = id.toString()
    }

    /**
     * Puts focus on [id] now, while the list stays on screen. For the item
     * that took the cursor with it when a refresh dropped it from the list:
     * the caller names a neighbour and this hands focus over, instead of
     * leaving it to fall to whatever is first.
     */
    fun restore(id: Any) {
        pending.value = id.toString()
        restores.value++
    }

    internal fun target(): String = pending.value

    internal fun clear() {
        pending.value = ""
    }
}

/**
 * [bringIntoView] is the fallback for a list that can come back with the item
 * scrolled out of range — a paged grid restoring an offset into pages it has not
 * replayed yet. It receives the key that [FocusReturn.opened] was given, so a
 * grid keyed by position converts it back with `toIntOrNull()`.
 */
@Composable
fun rememberFocusReturn(bringIntoView: suspend (String) -> Unit = {}): FocusReturn {
    val enabled = LocalIsTv.current
    val pending = rememberSaveable { mutableStateOf("") }
    val restores = remember { mutableStateOf(0) }
    val requester = remember { FocusRequester() }
    val focusReturn = remember(enabled) { FocusReturn(pending, restores, requester, enabled) }

    // Fires when the list re-enters composition, which is exactly the moment
    // worth restoring, and again on each [FocusReturn.restore]. Keying it on
    // the item count instead would re-fire as paging appends a page and yank
    // focus back mid-scroll.
    LaunchedEffect(restores.value) {
        val target = focusReturn.target()
        if (!enabled || target.isEmpty()) return@LaunchedEffect

        // Ask before scrolling. The restored offset usually has the item on
        // screen already, and scrolling to it would throw that offset away to
        // pin the item against the top edge — the jump this exists to avoid.
        // The item is often a frame or two late all the same, because paging
        // replays its cached pages after the first composition.
        if (!requester.claimFocusWithin(FramesBeforeScroll)) {
            bringIntoView(target)
            requester.claimFocusWithin(RestoreFrames)
        }

        // Cleared either way. A target that never appears — a film dropped by a
        // refresh, a rail that came back shorter — would otherwise spend the
        // whole retry budget again on every later return to this screen.
        focusReturn.clear()
    }
    return focusReturn
}

/** True as soon as the requester has something to give focus to. */
private suspend fun FocusRequester.claimFocusWithin(frames: Int): Boolean {
    repeat(frames) {
        // Losing the race with disposal is not worth crashing over.
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
