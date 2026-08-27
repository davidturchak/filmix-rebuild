package net.filmix.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.FocusChip
import net.filmix.core.designsystem.component.TextButton
import net.filmix.core.designsystem.component.PrimaryButton
import net.filmix.core.model.CatalogFilter
import net.filmix.core.model.FilterOption
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.QualityFilter
import net.filmix.core.model.previewOptions

/**
 * Filter picker, mirroring the groups the original app offered: content type,
 * genre, country, year, voice-over and quality flags.
 *
 * Countries (209) and years (117) are far too many to show flat, so each group
 * collapses until expanded. Countries name their own preview — see
 * [PinnedCountries] — because alphabetical order buries every country anyone
 * actually filters by.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    options: FilterOptions,
    filter: CatalogFilter,
    onFilterChange: (CatalogFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Фильтры",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!filter.isEmpty) {
                    TextButton(onClick = onClear) { Text("Сбросить") }
                }
                PrimaryButton(onClick = onDismiss) {
                    Text("Готово")
                }
            }
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item("type") {
                Group("Тип", options.sections, filter.sections) {
                    onFilterChange(filter.toggleSection(it))
                }
            }
            item("genre") {
                Group("Жанр", options.genres, filter.genres) {
                    onFilterChange(filter.toggleGenre(it))
                }
            }
            item("country") {
                Group("Страна", options.countries, filter.countries, PinnedCountries) {
                    onFilterChange(filter.toggleCountry(it))
                }
            }
            item("year") {
                Group("Год", options.years, filter.years) {
                    onFilterChange(filter.toggleYear(it))
                }
            }
            item("voice") {
                Group("Озвучка", options.voices, filter.voices) {
                    onFilterChange(filter.toggleVoice(it))
                }
            }
            item("quality") {
                QualityGroup(filter.qualities) { onFilterChange(filter.toggleQuality(it)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(
    title: String,
    options: List<FilterOption>,
    selected: Set<Int>,
    pinned: List<Int> = emptyList(),
    onToggle: (Int) -> Unit,
) {
    if (options.isEmpty()) return

    // What the group shows while collapsed. Selected values always stay
    // visible, so the user can see and undo a choice without re-expanding.
    // [pinned] names the preview outright; otherwise it is the first
    // PREVIEW_COUNT, which only reads well for a group with a useful order.
    val preview = remember(options, selected, pinned) {
        previewOptions(options, selected, pinned, limit = PREVIEW_COUNT)
    }

    // Start expanded only when the group is short enough to be scannable.
    var expanded by rememberSaveable(title) { mutableStateOf(options.size <= preview.size) }
    val visible = if (expanded) options else preview

    Column(Modifier.padding(vertical = 8.dp)) {
        // The toggle sits next to the title rather than pushed to the far edge:
        // D-pad focus search works in a vertical beam, so a right-aligned
        // control in a header row of its own is only reachable from whichever
        // chip below happens to share its column. Walking down the country
        // group skipped "Ещё 203" entirely, which made the group impossible to
        // expand by remote.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selected.isEmpty()) title else "$title (${selected.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (options.size > preview.size) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Свернуть" else "Ещё ${options.size - preview.size}")
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visible.forEach { option ->
                // Keyed, so a chip keeps its identity if the list ever does
                // reorder: a FlowRow is not a lazy layout and its children are
                // otherwise positional, which is what let focus end up on a
                // different country than the one it was pointing at.
                key(option.id) {
                    FocusChip(
                        selected = option.id in selected,
                        onClick = { onToggle(option.id) },
                        label = option.label,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityGroup(selected: Set<QualityFilter>, onToggle: (QualityFilter) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            text = if (selected.isEmpty()) "Качество" else "Качество (${selected.size})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityFilter.entries.forEach { quality ->
                FocusChip(
                    selected = quality in selected,
                    onClick = { onToggle(quality) },
                    label = quality.label,
                )
            }
        }
    }
}

private const val PREVIEW_COUNT = 12

/**
 * Countries worth showing before the group is expanded.
 *
 * The backend returns all 209 and the group previewed the first twelve
 * alphabetically, so a user opening the filters was offered Австралия, Австрия,
 * Азербайджан and Албания while every country they might plausibly pick sat
 * behind "Ещё 197".
 *
 * Ids, not labels: the id is what the catalog query is built from, and it does
 * not move if a label is retranslated. Verified against
 * `GET /api/v2/filter_list`, which returns these labels for both `app_lang=ru`
 * and `app_lang=uk`.
 */
private val PinnedCountries = listOf(
    6, // Россия
    18, // Израиль
    2, // США
    12, // Корея
    8, // Франция
    3, // Германия
)
