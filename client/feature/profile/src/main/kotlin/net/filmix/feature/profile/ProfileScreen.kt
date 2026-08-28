package net.filmix.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.filmix.core.data.PairingState
import net.filmix.core.designsystem.component.OutlinedButton
import net.filmix.core.designsystem.component.PrimaryButton

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
                    PrimaryButton(onClick = onStartPairing) {
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
                            PrimaryButton(onClick = onStartPairing) { Text("Новый код") }
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
                    OutlinedButton(onClick = onSignOut) {
                        Text("Выйти")
                    }
                }

                else -> Unit
            }

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
                    PrimaryButton(onClick = onStartPairing) { Text("Повторить") }
                }

                else -> Unit
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
