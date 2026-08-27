package net.filmix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.filmix.core.designsystem.theme.Dimens
import net.filmix.core.designsystem.theme.RatingNegative
import net.filmix.core.designsystem.theme.RatingNeutral
import net.filmix.core.designsystem.theme.RatingPositive

/**
 * The single browsing primitive: poster art first, title underneath, quality
 * badge overlaid. Replaces the original's text-dense list row where the poster
 * was a 95dp thumbnail beside four lines of metadata.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    rating: Int? = null,
    subtitle: String? = null,
    width: Dp = Dimens.posterWidth,
    height: Dp = Dimens.posterHeight,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    // One interaction source drives both the click and the focus ring, so a
    // D-pad landing on the card lights up the same element the remote will act on.
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(width)
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick,
                    )
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                },
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
            if (rating != null) {
                RatingBadge(
                    rating = rating,
                    modifier = Modifier.align(Alignment.TopStart),
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

/**
 * Site score, rendered the way the original app does it: a small tab pinned to
 * the poster's top-left, green when positive, red when negative, grey at zero,
 * with the value signed (`+14`, `-3`, `0`).
 */
@Composable
fun RatingBadge(rating: Int, modifier: Modifier = Modifier) {
    val background = when {
        rating > 0 -> RatingPositive
        rating < 0 -> RatingNegative
        else -> RatingNeutral
    }
    Text(
        text = if (rating > 0) "+$rating" else rating.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            // Square on the poster edge, rounded away from it — the original's
            // 0dp/1dp corner asymmetry, nudged up so it reads at this size.
            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
            .background(background)
            .widthIn(min = 36.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Kept for callers that still want to surface the release/rip label. */
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
