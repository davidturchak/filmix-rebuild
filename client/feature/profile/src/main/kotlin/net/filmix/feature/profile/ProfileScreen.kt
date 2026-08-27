package net.filmix.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.filmix.core.data.PairingState
import net.filmix.core.model.AppVersion
import net.filmix.core.model.UpdateState

sealed interface ProfileUiState {
    data object Idle : ProfileUiState
    data object Requesting : ProfileUiState
    data class AwaitingLink(
        val userCode: String,
        val state: PairingState = PairingState.Waiting,
    ) : ProfileUiState

    data class SignedIn(
        val displayName: String,
        val isPro: Boolean,
        val proUntil: String,
    ) : ProfileUiState

    data class Failed(val message: String) : ProfileUiState
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    modifier: Modifier = Modifier,
    onStartPairing: () -> Unit = {},
    onSignOut: () -> Unit = {},
    preferredQuality: Int? = null,
    onQualityChange: (Int?) -> Unit = {},
    version: AppVersion? = null,
    updateState: UpdateState = UpdateState.Idle,
    canInstallUpdates: Boolean = true,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onGrantInstallPermission: () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            when (state) {
                ProfileUiState.Idle -> {
                    Text(
                        "Вход в аккаунт",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Привяжите устройство, чтобы синхронизировать избранное, историю и PRO-подписку.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onStartPairing, shape = MaterialTheme.shapes.extraLarge) {
                        Text("Получить код")
                    }
                }

                ProfileUiState.Requesting -> CircularProgressIndicator()

                is ProfileUiState.AwaitingLink -> {
                    Text(
                        "Код привязки",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    CodeCard(state.userCode)
                    Text(
                        "Откройте filmix.biz в браузере, войдите в аккаунт и введите этот код.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    when (state.state) {
                        PairingState.Waiting -> Row3 { CircularProgressIndicator(); Text("Ожидание…") }
                        PairingState.Expired -> {
                            Text(
                                "Срок действия кода истёк.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(onClick = onStartPairing) { Text("Новый код") }
                        }

                        is PairingState.Linked -> Text("Устройство привязано")
                    }
                }

                is ProfileUiState.SignedIn -> {
                    Text(
                        state.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        if (state.isPro) "PRO до ${state.proUntil}" else "Обычный аккаунт",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onSignOut, shape = MaterialTheme.shapes.extraLarge) {
                        Text("Выйти")
                    }
                }

                else -> Unit
            }

            QualityPreference(preferredQuality, onQualityChange)

            when (state) {
                is ProfileUiState.Failed -> {
                    Text(
                        "Не удалось получить код",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onStartPairing) { Text("Повторить") }
                }

                else -> Unit
            }

            UpdateSection(
                state = updateState,
                canInstall = canInstallUpdates,
                onCheck = onCheckUpdate,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
                onGrantPermission = onGrantInstallPermission,
            )

            if (version != null) {
                VersionFooter(version)
            }
        }
    }
}

/**
 * Build identity. Shown in full rather than as a bare version name so a build
 * on a device can be matched to a commit — several builds share a version name
 * during development.
 */
@Composable
private fun VersionFooter(version: AppVersion) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 32.dp),
    ) {
        Text(
            "Filmix Client",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            version.full,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Preferred stream height. "Авто" means always take the best the source
 * offers, which is what StreamLink.selectQuality falls back to.
 */
@Composable
private fun QualityPreference(selected: Int?, onChange: (Int?) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 24.dp),
    ) {
        Text(
            "Качество по умолчанию",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Pair<String, Int?>>(
                "Авто" to null,
                "480p" to 480,
                "720p" to 720,
                "1080p" to 1080,
                "4K" to 2160,
            ).forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onChange(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun CodeCard(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, letterSpacing = 10.sp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun Row3(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
