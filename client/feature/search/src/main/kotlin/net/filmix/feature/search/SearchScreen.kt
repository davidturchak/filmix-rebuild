package net.filmix.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.component.FilledTonalIconButton
import net.filmix.core.designsystem.component.IconButton
import net.filmix.core.designsystem.component.PosterCard
import net.filmix.core.designsystem.component.rememberFocusReturn
import net.filmix.core.designsystem.component.describeError
import net.filmix.core.designsystem.component.rememberVoiceSearch
import net.filmix.core.designsystem.theme.LocalIsTv
import net.filmix.core.designsystem.theme.LocalDimensions
import net.filmix.core.model.Post
import net.filmix.core.model.voiceLanguageBadge
import java.util.Locale

/**
 * A collapsed caret sitting on the last character — the only state from which
 * right should give up the field rather than move the caret. A selection is
 * never "at the end": right collapses it first, as in any text field.
 */
private fun TextFieldValue.caretAtEnd(): Boolean =
    selection.collapsed && selection.end == text.length

private fun TextFieldValue.caretAtStart(): Boolean =
    selection.collapsed && selection.start == 0

@Composable
fun SearchScreen(
    query: String,
    suggestions: List<Post>,
    results: LazyPagingItems<Post>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    searched: Boolean = false,
    /** Already resolved: the stored choice, or the device's own language. */
    voiceLanguageTag: String = Locale.getDefault().toLanguageTag(),
    onQueryChange: (String) -> Unit = {},
    onSubmit: () -> Unit = {},
    onClear: () -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onVoiceResult: (String) -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // The caret position is needed to know when a D-pad press should stop
    // moving the caret and leave the field instead, and the String overload of
    // OutlinedTextField does not report it. The query stays the source of
    // truth: this only mirrors it, and follows it when it changes elsewhere —
    // a voice result, or the clear button.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    if (query != field.text) {
        field = TextFieldValue(query, TextRange(query.length))
    }

    // On a remote, typing means driving an on-screen grid key by key. Voice is
    // the faster path and is what the original app offered on TV.
    val voice = rememberVoiceSearch(
        prompt = "Что найти?",
        languageTag = voiceLanguageTag,
        onResult = onVoiceResult,
    )
    val languageBadge = voiceLanguageBadge(voiceLanguageTag)
    val micFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }


    val isTv = LocalIsTv.current

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalDimensions.current.gutter, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading, so the D-pad meets it on the way in from the rail —
            // and on TV voice is the primary input anyway.
            if (voice.available && isTv) {
                MicButton(micFocus, voice.listening, languageBadge, voice::start)
            }
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != query) onQueryChange(it.text)
            },
            singleLine = true,
            placeholder = { Text("Фильмы, сериалы, актёры") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSubmit()
                    keyboard?.hide()
                },
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(1f)
                .focusRequester(fieldFocus)
                // A focused text field swallows D-pad up/down as caret
                // movement, and does so even when it is single-line and the
                // caret has nowhere to go — so focus could never leave the
                // field downwards and the results underneath were unreachable
                // by remote. Preview the key and move focus ourselves, only
                // claiming it when something actually took the focus.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            // Sideways leaves the field. On a remote that is
                            // simply what left and right mean: the row is mic,
                            // field, clear button, and nobody edits a caret
                            // with a D-pad — while the on-screen keyboard is up
                            // it owns these keys anyway. Where a real keyboard
                            // is plausible the caret keeps them until it
                            // reaches the end of the text, so editing still
                            // behaves, and only then do the ends stop being
                            // dead ends.
                            //
                            // moveFocus, not requestFocus: it reports whether
                            // focus actually moved, so a press that could not
                            // go anywhere is left for the field instead of
                            // being swallowed. requestFocus returns nothing and
                            // fails silently.
                            //
                            // None of this reaches the app while the on-screen
                            // keyboard is up — the keyboard window owns the
                            // D-pad then, which is why the field can look
                            // frozen after it regains focus and re-opens it.
                            // Back closes the keyboard and navigation resumes,
                            // as anywhere else in Android.
                            Key.DirectionRight ->
                                (isTv || field.caretAtEnd()) &&
                                    focusManager.moveFocus(FocusDirection.Right)

                            Key.DirectionLeft ->
                                (isTv || field.caretAtStart()) &&
                                    focusManager.moveFocus(FocusDirection.Left)

                            else -> false
                        }
                    }
                },
        )
            // A sibling of the field rather than its trailing icon. Inside the
            // field it was unreachable by remote and unfixable from outside:
            // right could not descend into it, the field's own key handler sat
            // above it and stole presses aimed at it, and left from it walked
            // straight past the field to the mic. Out here it is an ordinary
            // neighbour that a directional search can find.
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClear()
                        // This button goes away with the text it clears, and
                        // focus would go with it — to the navigation rail, of
                        // all places, just as the user gets an empty field.
                        fieldFocus.requestFocus()
                    },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Очистить")
                }
            }

            if (voice.available && !isTv) {
                MicButton(micFocus, voice.listening, languageBadge, voice::start)
            }
        }

        // Suggestions replace the grid while typing; committing a query swaps
        // back to paged results.
        if (suggestions.isNotEmpty()) {
            Column(Modifier.padding(horizontal = LocalDimensions.current.gutter)) {
                suggestions.forEach { item ->
                    SuggestionRow(item, onClick = { onPostClick(item) })
                }
            }
            return@Column
        }

        voice.lastError?.let { code ->
            Text(
                describeError(code),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = LocalDimensions.current.gutter),
            )
        }

        ResultsGrid(results, compact, searched, onPostClick)
    }
}

/**
 * The microphone, with the language it listens in named underneath.
 *
 * Which language that is used to be invisible — the recogniser took the device
 * locale, and on an en-US TV a Russian catalog was searched in English with
 * nothing on screen to say so. Two letters under the button is enough to notice
 * it, and Настройки is where it changes.
 */
@Composable
private fun MicButton(
    focusRequester: FocusRequester,
    listening: Boolean,
    languageBadge: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = if (listening) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                null
            },
            // While listening the container is the accent itself, so the accent
            // ring would vanish exactly when the button matters most.
            ringColor = if (listening) MaterialTheme.colorScheme.onPrimary else null,
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = "Голосовой поиск ($languageBadge)")
        }
        Text(
            languageBadge,
            style = MaterialTheme.typography.labelSmall,
            color = if (listening) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ResultsGrid(
    results: LazyPagingItems<Post>,
    compact: Boolean,
    searched: Boolean,
    onPostClick: (Post) -> Unit,
) {
    val refreshing = results.loadState.refresh is LoadState.Loading
    val failed = results.loadState.refresh is LoadState.Error
    val gridState = rememberLazyGridState()
    val focusReturn = rememberFocusReturn { key ->
        val index = key.toIntOrNull() ?: return@rememberFocusReturn
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            gridState.scrollToItem(index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            refreshing -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            failed -> Text(
                "Не удалось выполнить поиск",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            // An empty grid means two different things, and telling the user
            // to start typing after they have just searched reads as if the
            // query never arrived.
            results.itemCount == 0 -> Text(
                if (searched) "Ничего не найдено" else "Начните вводить название",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(
                    minSize = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                ),
                contentPadding = PaddingValues(LocalDimensions.current.gutter),
                horizontalArrangement = Arrangement.spacedBy(LocalDimensions.current.railGap),
                verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.sectionGap),
            ) {
                items(count = results.itemCount) { index ->
                    val post = results[index] ?: return@items
                    PosterCard(
                        modifier = focusReturn.modifier(index),
                        title = post.title,
                        posterUrl = post.posterUrl,
                        rating = post.rating,
                        subtitle = post.lastEpisode?.label
                            ?: post.year.takeIf { it > 0 }?.toString(),
                        width = if (compact) LocalDimensions.current.posterWidthCompact else LocalDimensions.current.posterWidth,
                        height = if (compact) LocalDimensions.current.posterHeightCompact else LocalDimensions.current.posterHeight,
                        onClick = {
                            focusReturn.opened(index)
                            onPostClick(post)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(post: Post, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = post.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 60.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                post.originalTitle.takeIf { it.isNotEmpty() && it != post.title },
                post.year.takeIf { it > 0 }?.toString(),
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
