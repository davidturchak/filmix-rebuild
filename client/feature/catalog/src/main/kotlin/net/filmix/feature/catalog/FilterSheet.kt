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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.filmix.core.designsystem.component.FocusChip
import net.filmix.core.model.CatalogFilter
import net.filmix.core.model.FilterOption
import net.filmix.core.model.FilterOptions
import net.filmix.core.model.QualityFilter

/**
 * Filter picker, mirroring the groups the original app offered: content type,
 * genre, country, year, voice-over and quality flags.
 *
 * Countries (209) and years (117) are far too many to show flat, so each group
 * collapses and shows a capped preview until expanded.
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
                Button(onClick = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
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
                Group("Страна", options.countries, filter.countries) {
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
    onToggle: (Int) -> Unit,
) {
    if (options.isEmpty()) return
    // Start expanded only when the group is short enough to be scannable.
    var expanded by rememberSaveable(title) { mutableStateOf(options.size <= PREVIEW_COUNT) }

    // Selected values always stay visible, even while collapsed, so the user
    // can see and undo a choice without re-expanding.
    val visible = remember(options, selected, expanded) {
        if (expanded) {
            options
        } else {
            val chosen = options.filter { it.id in selected }
            (chosen + options.filterNot { it.id in selected }).take(PREVIEW_COUNT)
        }
    }

    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selected.isEmpty()) title else "$title (${selected.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (options.size > PREVIEW_COUNT) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Свернуть" else "Ещё ${options.size - PREVIEW_COUNT}")
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
                FocusChip(
                    selected = option.id in selected,
                    onClick = { onToggle(option.id) },
                    label = option.label,
                )
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
