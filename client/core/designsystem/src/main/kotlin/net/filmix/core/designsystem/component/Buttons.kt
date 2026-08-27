package net.filmix.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * The filled primary button, with focus you can actually see.
 *
 * Material renders focus on a filled button as a 10% state layer of the content
 * colour — on the brand orange that is a barely perceptible lightening, so every
 * primary action in the app looked permanently unfocused on a TV. The accent
 * focus ring cannot help either: it is the same orange as the fill. This rings
 * in `onPrimary` instead.
 *
 * Use this rather than Material's `Button` anywhere a filled action is wanted;
 * the focus ring is the whole point of having it.
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
