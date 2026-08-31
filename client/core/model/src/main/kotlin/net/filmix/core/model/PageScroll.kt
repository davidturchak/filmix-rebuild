package net.filmix.core.model

/**
 * One D-pad press worth of a scrollable text window.
 *
 * A remote has no way to scroll a block of text: focus search moves between
 * focusable nodes and nothing inside a paragraph is one, so a reader has to
 * claim UP and DOWN for itself. The arithmetic is the easy half. The half
 * worth testing is the handoff — a press that still has text to reveal belongs
 * to the window, and the press that finds the end belongs to focus search, or
 * the cursor is trapped in the text and the close button is unreachable.
 */
object PageScroll {

    /**
     * The fraction of the window kept on screen across a press. A clean page
     * turn loses the line straddling the fold, which is the line the reader
     * stopped on.
     */
    const val OVERLAP = 0.15f

    /**
     * The offset to scroll to, in pixels, or null when the press is not this
     * window's to claim: it has not been measured yet, its text fits, or it is
     * already at that end.
     *
     * The viewport guard comes first because it doubles as the measured check —
     * an unmeasured scroll state reports a viewport of zero alongside a
     * meaningless [maxValue].
     */
    fun target(value: Int, maxValue: Int, viewport: Int, forward: Boolean): Int? {
        if (viewport <= 0 || maxValue <= 0) return null
        if (forward && value >= maxValue) return null
        if (!forward && value <= 0) return null
        // At least one pixel: a press that is consumed but moves nothing reads
        // as a dead remote.
        val step = (viewport * (1f - OVERLAP)).toInt().coerceAtLeast(1)
        return (if (forward) value + step else value - step).coerceIn(0, maxValue)
    }
}
