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

/** How long to keep trying before giving up on an item that never appears. */
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
    private val pending: MutableState<Int>,
    internal val requester: FocusRequester,
    internal val enabled: Boolean,
) {
    /**
     * Attach to every item, identified by anything stable — a list index or a
     * post id. Only the item being returned to actually claims the requester,
     * so there is never more than one holder.
     */
    fun modifier(id: Int): Modifier =
        if (enabled && id == pending.value) Modifier.focusRequester(requester) else Modifier

    /** Call from the item's click handler, before navigating away. */
    fun opened(id: Int) {
        pending.value = id
    }

    internal fun target(): Int = pending.value

    internal fun clear() {
        pending.value = -1
    }
}

/**
 * [bringIntoView] is for lists that can come back with the item scrolled out of
 * range — a paged grid restoring an offset into pages it has not replayed yet.
 * Rails need nothing: their offsets come back with them.
 */
@Composable
fun rememberFocusReturn(bringIntoView: suspend (Int) -> Unit = {}): FocusReturn {
    val enabled = LocalIsTv.current
    val pending = rememberSaveable { mutableStateOf(-1) }
    val requester = remember { FocusRequester() }
    val focusReturn = remember(enabled) { FocusReturn(pending, requester, enabled) }

    // Keyed on Unit: this fires when the list re-enters composition, which is
    // exactly the moment worth restoring. Keying it on the item count instead
    // would re-fire as paging appends a page and yank focus back mid-scroll.
    LaunchedEffect(Unit) {
        val target = focusReturn.target()
        if (!enabled || target < 0) return@LaunchedEffect
        bringIntoView(target)
        // The item is usually a frame or two behind: paging replays its cached
        // pages after the first composition, so requesting focus straight away
        // hits a requester with nothing attached to it yet. Retrying per frame
        // costs nothing and needs no per-layout notion of "is it visible".
        repeat(RestoreFrames) {
            if (runCatching { requester.requestFocus() }.isSuccess) {
                focusReturn.clear()
                return@LaunchedEffect
            }
            withFrameNanos {}
        }
    }
    return focusReturn
}
