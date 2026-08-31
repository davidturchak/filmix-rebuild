package net.filmix.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.FocusChip
import net.filmix.core.model.AppVersion
import net.filmix.core.model.ExternalPlayer
import net.filmix.core.model.UpdateState
import net.filmix.core.model.voiceLanguageBadge
import net.filmix.core.model.voiceLanguages

@Composable
fun ConfigScreen(
    preferredQuality: Int?,
    players: List<ExternalPlayer>,
    selectedPlayerPackage: String?,
    updateState: UpdateState,
    modifier: Modifier = Modifier,
    voiceLanguage: String? = null,
    systemLanguageTag: String = "",
    onQualityChange: (Int?) -> Unit = {},
    onPlayerChange: (String?) -> Unit = {},
    onVoiceLanguageChange: (String?) -> Unit = {},
    canInstallUpdates: Boolean = true,
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onGrantInstallPermission: () -> Unit = {},
    version: AppVersion? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            // Three sections plus the footer overflow the 540dp-tall TV window.
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            QualityPreference(preferredQuality, onQualityChange)

            PlayerChoice(players, selectedPlayerPackage, onPlayerChange)

            VoiceLanguageChoice(voiceLanguage, systemLanguageTag, onVoiceLanguageChange)

            UpdateSection(
                state = updateState,
                installed = version,
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
 * Preferred stream height. "Авто" means always take the best the source
 * offers, which is what StreamLink.selectQuality falls back to.
 */
@Composable
private fun QualityPreference(selected: Int?, onChange: (Int?) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                FocusChip(
                    selected = selected == value,
                    onClick = { onChange(value) },
                    label = label,
                )
            }
        }
    }
}

/**
 * Which app plays the stream. The built-in chip also reads as selected when
 * the stored package is no longer installed — playback falls back to the
 * built-in player in that case, and the list should not claim otherwise.
 */
@Composable
private fun PlayerChoice(
    players: List<ExternalPlayer>,
    selectedPackage: String?,
    onChange: (String?) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            "Видеоплеер",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FocusChip(
            selected = selectedPackage == null || players.none { it.packageName == selectedPackage },
            onClick = { onChange(null) },
            label = "Встроенный",
        )
        players.forEach { player ->
            FocusChip(
                selected = player.packageName == selectedPackage,
                onClick = { onChange(player.packageName) },
                label = player.label,
            )
        }
        Text(
            "Позиция просмотра сохраняется, если внешний плеер её сообщает (MX Player, VLC).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Which language the microphone listens in.
 *
 * "Как в системе" names the language it resolves to, because that is the whole
 * trap this setting exists for: the TVs this runs on ship as en-US, so the
 * device default listened in English for a Russian catalog and nothing on
 * screen said so.
 */
@Composable
private fun VoiceLanguageChoice(
    selectedTag: String?,
    systemLanguageTag: String,
    onChange: (String?) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            "Язык голосового поиска",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            voiceLanguages.forEach { language ->
                FocusChip(
                    selected = language.tag == selectedTag,
                    onClick = { onChange(language.tag) },
                    label = if (language.tag == null && systemLanguageTag.isNotEmpty()) {
                        "${language.label} (${voiceLanguageBadge(systemLanguageTag)})"
                    } else {
                        language.label
                    },
                )
            }
        }
        Text(
            "Распознавание — системное: язык, для которого на устройстве нет данных, ничего не вернёт.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
            "Filmix-ng by Ku4eR",
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
