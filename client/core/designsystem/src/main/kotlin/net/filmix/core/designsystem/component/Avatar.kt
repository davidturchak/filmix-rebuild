package net.filmix.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * A round account picture, or a silhouette when there is none.
 *
 * The fallback is drawn rather than fetched: the backend's "no avatar" answer
 * is a path on the website that no image loader here can resolve — see
 * `Avatar.urlOrNull`, which turns it into the null this takes.
 *
 * The silhouette sits on [androidx.compose.material3.ColorScheme.surface] so
 * it reads as a filled disc and not as a stray glyph, and its inset is a sixth
 * of the size so the proportion holds from a 36dp comment row up to the
 * profile screen's portrait.
 */
@Composable
fun Avatar(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Icon(
            Icons.Filled.Person,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(size / 6),
        )
    }
}
