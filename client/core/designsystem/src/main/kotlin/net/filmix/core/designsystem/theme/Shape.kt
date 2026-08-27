package net.filmix.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The original app topped out at 10dp corners, which reads flat. Cards go to
 * 12dp and pill-shaped actions to 28dp.
 */
val FilmixShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object Dimens {
    val gutter = 24.dp
    val railGap = 12.dp
    val sectionGap = 28.dp

    /** 2:3 poster art. Sized for a ~1180dp-wide tablet showing roughly 7 per rail. */
    val posterWidth = 140.dp
    val posterHeight = 210.dp
    val posterWidthCompact = 108.dp
    val posterHeightCompact = 162.dp

    val heroHeight = 420.dp
    val heroHeightCompact = 280.dp
}
