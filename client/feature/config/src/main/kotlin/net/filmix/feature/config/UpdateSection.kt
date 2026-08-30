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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                TextButton(onClick = onCheck) {
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
                    PrimaryButton(onClick = onGrantPermission) {
                        Text("Открыть настройки")
                    }
                } else {
                    PrimaryButton(onClick = onDownload) {
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
                PrimaryButton(onClick = onInstall) {
                    Text("Установить")
                }
            }

            state is UpdateState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Не удалось проверить обновления",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onCheck) { Text("Повторить") }
            }
        }
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
