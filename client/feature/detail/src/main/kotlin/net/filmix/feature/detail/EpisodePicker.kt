package net.filmix.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.FocusChip
import net.filmix.core.model.Episode
import net.filmix.core.model.Season
import net.filmix.core.model.SeriesPlaylist
import net.filmix.core.model.SeriesTranslation

/** What the user currently has selected in the episode tree. */
data class EpisodeSelection(
    val season: String? = null,
    val translation: String? = null,
) {
    /**
     * Resolves the selection against the tree, falling back to the first
     * available option. A translation chosen in one season often does not
     * exist in another, so it is re-resolved rather than carried blindly.
     */
    fun resolve(playlist: SeriesPlaylist): Pair<Season, SeriesTranslation>? {
        val chosenSeason = playlist.seasons.firstOrNull { it.number == season }
            ?: playlist.seasons.firstOrNull()
            ?: return null
        val chosenTranslation = chosenSeason.translations.firstOrNull { it.name == translation }
            ?: chosenSeason.translations.firstOrNull()
            ?: return null
        return chosenSeason to chosenTranslation
    }
}

/**
 * Season, voice-over and episode pickers.
 *
 * The original app buried this behind tabs and expandable folders; a title
 * with five seasons and four competing translations is easier to navigate as
 * three rows of chips than as a nested tree.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodePicker(
    playlist: SeriesPlaylist,
    selection: EpisodeSelection,
    modifier: Modifier = Modifier,
    onSelectSeason: (String) -> Unit = {},
    onSelectTranslation: (String) -> Unit = {},
    onPlayEpisode: (Episode) -> Unit = {},
) {
    val resolved = selection.resolve(playlist) ?: return
    val (season, translation) = resolved

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (playlist.seasons.size > 1) {
            PickerRow(label = "Сезон") {
                playlist.seasons.forEach { option ->
                    FocusChip(
                        selected = option.number == season.number,
                        onClick = { onSelectSeason(option.number) },
                        label = option.label,
                    )
                }
            }
        }

        if (season.translations.size > 1) {
            PickerRow(label = "Озвучка") {
                season.translations.forEach { option ->
                    FocusChip(
                        selected = option.name == translation.name,
                        onClick = { onSelectTranslation(option.name) },
                        label = option.name,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${translation.episodes.size} серий",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                translation.episodes.forEach { episode ->
                    FocusChip(
                        selected = false,
                        onClick = { onPlayEpisode(episode) },
                        label = episode.number,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
        }
    }
}
