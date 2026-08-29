package net.filmix.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.FocusChip
import net.filmix.core.designsystem.component.enterFocusAt
import net.filmix.core.model.Episode
import net.filmix.core.model.EpisodeWatchState
import net.filmix.core.model.Season
import net.filmix.core.model.SeasonWatch
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
    watch: SeasonWatch? = null,
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
            val current = watch?.current
            // Keyed on the number: a new chip is the target when the current
            // episode moves — e.g. after finishing one and returning here.
            val currentFocus = remember(current?.number) { FocusRequester() }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = if (current == null) {
                    Modifier
                } else {
                    // D-pad entry into the grid lands on the current episode,
                    // not whichever chip is geometrically nearest. Safe to
                    // hand out the requester unconditionally — the FlowRow is
                    // not lazy, so the current chip is always composed and
                    // attached.
                    Modifier.enterFocusAt { currentFocus }
                },
            ) {
                translation.episodes.forEach { episode ->
                    val state = watch?.states?.get(episode.number) ?: EpisodeWatchState.None
                    val isCurrent = episode.number == current?.number
                    // The current chip already reads as selected, so it takes
                    // no "started" arrow — only a finished check.
                    val mark = when {
                        state == EpisodeWatchState.Finished ->
                            Icons.Filled.Check to "Просмотрена"

                        state == EpisodeWatchState.InProgress && !isCurrent ->
                            Icons.Filled.PlayArrow to "Начата"

                        else -> null
                    }
                    FocusChip(
                        selected = isCurrent,
                        onClick = { onPlayEpisode(episode) },
                        label = episode.number,
                        leadingIcon = mark?.let { (icon, description) ->
                            {
                                Icon(
                                    icon,
                                    contentDescription = description,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        },
                        modifier = if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier,
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
