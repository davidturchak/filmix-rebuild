package net.filmix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * Material's buttons, wrapped so that D-pad focus is visible.
 *
 * Material renders focus as a 10% state layer of the content colour, which at
 * three metres is no cue at all — and on a filled button whose ring would be
 * the fill colour, not even a ring helps. Each wrapper pairs the button with
 * [focusRing] through a shared interaction source, which is the whole reason
 * these exist.
 *
 * They deliberately carry the Material names (bar [PrimaryButton], which says
 * what it is for) so a call site reads unchanged and only the import moves.
 * Prefer these everywhere; a raw `androidx.compose.material3` button is a
 * control the remote cannot show the user it has landed on.
 */

/**
 * The filled primary button.
 *
 * Rings in `onPrimary`: the accent ring is invisible against an
 * accent-filled button, which is how every primary action in the app came to
 * look permanently unfocused.
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val buttonShape = shape ?: MaterialTheme.shapes.extraLarge
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = buttonShape,
        interactionSource = interaction,
        modifier = modifier.focusRing(
            shape = buttonShape,
            interactionSource = interaction,
            color = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

/** The outlined button. Its own outline reads as decoration, not as focus. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val buttonShape = shape ?: MaterialTheme.shapes.extraLarge
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = buttonShape,
        interactionSource = interaction,
        modifier = modifier.focusRing(
            shape = buttonShape,
            interactionSource = interaction,
        ),
        content = content,
    )
}

/**
 * The text button — the worst offender of the three, since it has no container
 * of its own for a state layer to tint.
 */
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Material's padding by default. A caller tightens it to sit a label flush
     * with a column of text beside it, and then owns keeping the focus ring
     * clear of what it surrounds.
     */
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        contentPadding = contentPadding,
        modifier = modifier.focusRing(interactionSource = interaction),
        content = content,
    )
}

/**
 * The plain icon button. Round, so the ring is too.
 *
 * It states its content colour rather than inheriting one. Material's
 * `IconButton` tints from `LocalContentColor`, which is only provided by an
 * enclosing `Surface` — and a button sitting straight on the page background,
 * as the search field's clear button does, therefore got the composition
 * local's own default of **black**. On this app's near-black ground that drew
 * the glyph darker than the surface behind it: measured at rgb(9,10,12) on
 * rgb(14,15,19), which is not a dim icon but an invisible one.
 *
 * [contentColor] is `onSurfaceVariant`, the muted foreground the search
 * field's own magnifier already uses, so a bare icon button reads as the
 * secondary control it is. An `Icon` may still override it with its own
 * `tint`, which is how the detail screen's back arrow takes `onBackground`.
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor),
        modifier = modifier.focusRing(
            shape = CircleShape,
            interactionSource = interaction,
        ),
        content = content,
    )
}

/**
 * The tonal icon button.
 *
 * [ringColor] is for a caller that recolours the container — the accent ring
 * disappears on an accent container the same way it does on a filled button,
 * so a button that turns orange while active (the voice-search mic) passes
 * `onPrimary` for that state.
 */
@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors? = null,
    ringColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors ?: IconButtonDefaults.filledTonalIconButtonColors(),
        interactionSource = interaction,
        modifier = modifier.focusRing(
            shape = CircleShape,
            interactionSource = interaction,
            color = ringColor,
        ),
        content = content,
    )
}
