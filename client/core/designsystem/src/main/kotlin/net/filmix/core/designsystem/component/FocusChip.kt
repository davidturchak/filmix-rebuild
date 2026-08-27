package net.filmix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A FilterChip that shows D-pad focus.
 *
 * Material3's chip renders its *selected* state but gives focus no visible
 * treatment, so on a remote the highlight never moves and the row looks dead
 * even though focus is traversing it correctly. Pairing the chip with
 * [focusRing] through a shared interaction source makes the movement visible.
 */
@Composable
fun FocusChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        interactionSource = interaction,
        modifier = modifier.focusRing(
            shape = MaterialTheme.shapes.small,
            scaleWhenFocused = 1.08f,
            interactionSource = interaction,
        ),
    )
}
