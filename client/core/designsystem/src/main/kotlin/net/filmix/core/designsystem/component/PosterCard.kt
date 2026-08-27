package net.filmix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.runtime.remember
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.theme.Dimens

/**
 * The single browsing primitive: poster art first, title underneath, quality
 * badge overlaid. Replaces the original's text-dense list row where the poster
 * was a 95dp thumbnail beside four lines of metadata.
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    quality: String? = null,
    subtitle: String? = null,
    width: Dp = Dimens.posterWidth,
    height: Dp = Dimens.posterHeight,
    onClick: () -> Unit = {},
) {
    // One interaction source drives both the click and the focus ring, so a
    // D-pad landing on the card lights up the same element the remote will act on.
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(width)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .size(width = width, height = height)
                .focusRing(shape = MaterialTheme.shapes.medium, interactionSource = interaction)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (quality != null) {
                QualityBadge(
                    text = quality,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
