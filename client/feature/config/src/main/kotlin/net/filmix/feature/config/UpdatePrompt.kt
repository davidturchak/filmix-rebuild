package net.filmix.feature.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.PrimaryButton
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.model.AppUpdate

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
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accept = remember { FocusRequester() }
    // A dialog arrives with nothing focused, and on a remote that reads as a
    // frozen screen: no cursor, and BACK the only key that does anything.
    LaunchedEffect(update.versionCode) { accept.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Доступна версия ${update.versionName}") },
        text = {
            Column {
                if (update.notes.isNotBlank()) {
                    Text(update.notes, style = MaterialTheme.typography.bodyMedium)
                }
                if (update.sizeLabel.isNotEmpty()) {
                    Text(
                        update.sizeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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
