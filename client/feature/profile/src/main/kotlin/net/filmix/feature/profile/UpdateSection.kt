package net.filmix.feature.profile

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
import net.filmix.core.model.UpdateState

/**
 * Update prompt. Only occupies space when there is something to say — an app
 * that is current shows nothing beyond the manual check.
 */
@Composable
fun UpdateSection(
    state: UpdateState,
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
        when (state) {
            UpdateState.Idle -> TextButton(onClick = onCheck) { Text("Проверить обновления") }

            UpdateState.Checking -> Text(
                "Проверка обновлений…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            UpdateState.UpToDate -> Text(
                "Установлена последняя версия",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateState.Available -> Card {
                Text(
                    "Доступна версия ${state.update.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.update.notes.isNotEmpty()) {
                    Text(
                        state.update.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
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

            is UpdateState.Downloading -> Card {
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

            is UpdateState.ReadyToInstall -> Card {
                Text(
                    "Версия ${state.update.versionName} загружена",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PrimaryButton(onClick = onInstall) {
                    Text("Установить")
                }
            }

            is UpdateState.Failed -> Column(
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
