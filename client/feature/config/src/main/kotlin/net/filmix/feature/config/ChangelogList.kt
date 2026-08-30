package net.filmix.feature.config

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.focusRing
import net.filmix.core.designsystem.component.rememberFocusInteraction
import net.filmix.core.designsystem.theme.LocalIsTv
import net.filmix.core.model.ReleaseNote

/**
 * What the user has missed, newest first — shared by the launch prompt and the
 * Настройки card so the two cannot drift.
 */
@Composable
fun ChangelogList(
    entries: List<ReleaseNote>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.forEach { entry ->
            ReleaseEntry(entry)
        }
    }
}

@Composable
private fun ReleaseEntry(entry: ReleaseNote) {
    val interaction = rememberFocusInteraction()
    Column(
        Modifier
            .fillMaxWidth()
            // Focusable on TV only. The D-pad scrolls by hopping between
            // focusable nodes, so with none inside, a changelog longer than the
            // container simply cannot be scrolled to the end. No lift: a
            // full-width block that scales reads as a glitch and its ring
            // clips at the container edge.
            .focusRing(scaleWhenFocused = 1f, interactionSource = interaction)
            .then(
                if (LocalIsTv.current) {
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
