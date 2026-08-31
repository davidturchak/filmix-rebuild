package net.filmix.feature.config

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.RAIL_WIDTH
import net.filmix.core.designsystem.component.ReadingRail
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.isFocused
import net.filmix.core.designsystem.component.overflows
import net.filmix.core.designsystem.component.pageOnDpad
import net.filmix.core.designsystem.component.rememberFocusInteraction
import net.filmix.core.designsystem.theme.LocalIsTv
import net.filmix.core.model.ReleaseNote

/**
 * What the user has missed, newest first — shared by the launch prompt and the
 * Настройки card so the two cannot drift.
 *
 * [focusableEntries] decides how a remote gets through a long list, and there
 * is no default that is right in both places. On the Настройки screen the list
 * sits in the page's own scroller, which the D-pad drives by hopping between
 * focusable nodes — so the entries have to be some, or the tail of the
 * changelog cannot be scrolled to. In [ScrollableChangelog] the window claims
 * UP and DOWN for itself, and focusable entries inside it would take those
 * presses back.
 */
@Composable
fun ChangelogList(
    entries: List<ReleaseNote>,
    modifier: Modifier = Modifier,
    focusableEntries: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.forEach { entry ->
            ReleaseEntry(entry, focusableEntries)
        }
    }
}

/**
 * The changelog in a window of its own, for the launch prompt.
 *
 * A dialog cannot grow with the changelog — a device several versions behind
 * would push the prompt's own buttons off the screen — so the list is bounded
 * by the caller and scrolls inside that bound. Which leaves a remote with
 * nothing to press: bounded text is not focusable, so the D-pad had no way in
 * and the entries past the fold could not be reached at all. The window reads
 * the keys itself instead, exactly as the detail screen's description reader
 * does, and shows the same rail so there is something on screen saying the
 * text goes on.
 *
 * Focusable only while it actually overflows. A window with nothing to scroll
 * draws no rail, so taking the cursor would leave it on a node with no mark on
 * it anywhere — and there would be nothing to do there in any case.
 */
@Composable
fun ScrollableChangelog(
    entries: List<ReleaseNote>,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val interaction = rememberFocusInteraction()
    val focused by interaction.isFocused()

    Box(modifier) {
        ChangelogList(
            entries = entries,
            focusableEntries = false,
            modifier = Modifier
                // Clear of the rail, which the Box lays over the leading edge.
                .padding(start = RAIL_WIDTH + 12.dp)
                .pageOnDpad(scroll)
                // Outside the scroller, deliberately: inside it the focus
                // target would take the size of the scrolled content rather
                // than of the window.
                .then(
                    if (scroll.overflows()) {
                        Modifier.focusable(interactionSource = interaction)
                    } else {
                        Modifier
                    },
                )
                .verticalScroll(scroll),
        )
        // matchParentSize rather than fillMaxHeight: the rail must be as tall
        // as the text turned out to be, not as tall as the bound it was given,
        // or a one-entry changelog reserves the whole window in blank space.
        ReadingRail(scroll, focused, Modifier.matchParentSize())
    }
}

@Composable
private fun ReleaseEntry(entry: ReleaseNote, focusable: Boolean) {
    val interaction = rememberFocusInteraction()
    Column(
        Modifier
            .fillMaxWidth()
            // No lift: a full-width block that scales reads as a glitch and its
            // ring clips at the container edge.
            .focusRing(scaleWhenFocused = 1f, interactionSource = interaction)
            .then(
                if (focusable && LocalIsTv.current) {
                    Modifier.focusable(interactionSource = interaction)
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            entry.versionName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        entry.notes.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
