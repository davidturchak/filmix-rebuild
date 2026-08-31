package net.filmix.feature.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.filmix.core.designsystem.component.PrimaryButton
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.model.AppUpdate
import net.filmix.core.model.AppVersion

/**
 * Raised over whatever is on screen when the launch check finds a release.
 *
 * The launch check is the only way an update reaches a device nobody opens
 * Настройки on — a TV in someone else's house. It never appears for a failed
 * or empty check, and "Позже" is remembered per release.
 */
@Composable
fun UpdatePrompt(
    update: AppUpdate,
    installed: AppVersion,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accept = remember { FocusRequester() }
    // A dialog arrives with nothing focused, and on a remote that reads as a
    // frozen screen: no cursor, and BACK the only key that does anything.
    //
    // The button lives in the dialog's own window, which is composed and laid
    // out after this effect first runs, so a single unguarded requestFocus is
    // the two failure modes this exists to avoid: it throws "FocusRequester is
    // not initialized" — on the launch path, so the app dies on start for
    // everyone a release is offered to — or it no-ops and leaves the dialog
    // with no cursor. Retry per frame and never crash over it, the same way
    // the detail screen claims focus for its play button.
    LaunchedEffect(update.versionCode) {
        repeat(FocusAttempts) {
            withFrameNanos { }
            if (runCatching { accept.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // BACK still closes it — on a remote that is how "not now" is said —
        // but a stray tap on the scrim must not count as "Позже", because that
        // answer is remembered and would silently bury the release for good.
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Доступна версия ${update.versionName}") },
        text = {
            Column {
                Text(
                    listOfNotNull(
                        "У вас ${installed.name}",
                        update.sizeLabel.takeIf { it.isNotEmpty() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Bounded: a device several versions behind gets several
                // entries, and the dialog must not push its own buttons off the
                // screen. UP from «Обновить» enters the window; it pages from
                // there and hands the cursor back at the end.
                ScrollableChangelog(
                    entries = update.changesSince(installed),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 220.dp),
                )
            }
        },
        confirmButton = {
            PrimaryButton(onClick = onAccept, modifier = Modifier.focusRequester(accept)) {
                Text("Обновить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}

/** Frames to keep trying for, before leaving the dialog unfocused. */
private const val FocusAttempts = 8
