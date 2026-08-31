package net.filmix.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.PrimaryButton
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.model.AppVersion
import net.filmix.core.model.UpdateState

/**
 * Update prompt. Only occupies space when there is something to say — an app
 * that is current shows nothing beyond the manual check.
 */
@Composable
fun UpdateSection(
    state: UpdateState,
    installed: AppVersion?,
    modifier: Modifier = Modifier,
    onCheck: () -> Unit = {},
    onDownload: () -> Unit = {},
    onInstall: () -> Unit = {},
    onGrantPermission: () -> Unit = {},
    canInstall: Boolean = true,
) {
    // Every press here swaps one branch of the when for another, so the button
    // the user pressed leaves composition and Compose drops the focus with it —
    // the cursor was landing back on the nav rail after every press. Hand it to
    // whatever replaced the control instead.
    val primary = remember { FocusRequester() }
    var claimFocus by remember { mutableStateOf(false) }
    val claiming = { action: () -> Unit -> { claimFocus = true; action() } }

    val stage = state.stage
    LaunchedEffect(stage) {
        if (!claimFocus || !stage.hasAction) return@LaunchedEffect
        // Same shape as the launch prompt's claim: the replacement is composed
        // after this runs, so yield a frame and never throw over it.
        repeat(FocusRestoreFrames) {
            withFrameNanos { }
            if (runCatching { primary.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Column(
        modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            // One button across every state that offers a check, rather than a
            // button replaced by a label: on a remote, swapping it out takes
            // the focus the user just pressed with it, leaving the screen with
            // no cursor. The result goes underneath instead. Which states those
            // are lives on UpdateState.offersCheck, where it is tested.
            state.offersCheck -> {
                TextButton(
                    onClick = claiming(onCheck),
                    modifier = Modifier.focusRequester(primary),
                ) {
                    Text(
                        if (state == UpdateState.Checking) {
                            "Проверка обновлений…"
                        } else {
                            "Проверить обновления"
                        },
                    )
                }
                if (state == UpdateState.UpToDate) {
                    Text(
                        "Установлена последняя версия",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state is UpdateState.Available -> Card {
                Text(
                    "Доступна версия ${state.update.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Left-aligned, unlike the rest of the card: centred text reads
                // badly as a bulleted list. ConfigScreen already scrolls, so a
                // tall card here needs no bound of its own.
                ChangelogList(entries = state.update.changesSince(installed?.code ?: 0))
                if (!canInstall) {
                    Text(
                        "Разрешите установку приложений из этого источника.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton(
                        onClick = claiming(onGrantPermission),
                        modifier = Modifier.focusRequester(primary),
                    ) {
                        Text("Открыть настройки")
                    }
                } else {
                    PrimaryButton(
                        onClick = claiming(onDownload),
                        modifier = Modifier.focusRequester(primary),
                    ) {
                        Text("Обновить · ${state.update.sizeLabel}")
                    }
                }
            }

            state is UpdateState.Downloading -> Card {
                Text(
                    "Загрузка ${state.percent}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state is UpdateState.ReadyToInstall -> Card {
                Text(
                    "Версия ${state.update.versionName} загружена",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PrimaryButton(
                    onClick = claiming(onInstall),
                    modifier = Modifier.focusRequester(primary),
                ) {
                    Text("Установить")
                }
            }

            state is UpdateState.Failed -> Failure(
                headline = "Не удалось проверить обновления",
                reason = state.message,
                onRetry = claiming(onCheck),
                focusRequester = primary,
            )

            state is UpdateState.DownloadFailed -> Failure(
                headline = "Не удалось скачать обновление",
                reason = state.message,
                onRetry = claiming(onDownload),
                focusRequester = primary,
            )
        }
    }
}

/**
 * A failed step, with what went wrong under it.
 *
 * The reason is shown rather than swallowed: it is where the HTTP code lands,
 * and a download that failed on "download HTTP 400" said only "не удалось
 * проверить обновления" on screen, which sent the diagnosis in exactly the
 * wrong direction. Unlocalised — it comes from the exception — so it is set
 * small and dim, under a headline that is not.
 */
@Composable
private fun Failure(
    headline: String,
    reason: String,
    onRetry: () -> Unit,
    focusRequester: FocusRequester,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            headline,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (reason.isNotBlank()) {
            Text(
                reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
        TextButton(
            onClick = onRetry,
            modifier = Modifier.focusRequester(focusRequester),
        ) { Text("Повторить") }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Frames to keep trying for, before leaving the cursor where it fell. */
private const val FocusRestoreFrames = 8
