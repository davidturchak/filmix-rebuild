package net.filmix.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Metrics that differ by device class.
 *
 * A TV is not simply a large tablet. The Google TV under test reports
 * 960x540dp — *less* height than the 738dp tablet — because it renders at
 * 1920x1080 with a 320dpi density. So a hero sized for a tablet overflows the
 * screen, while the type is simultaneously too small to read at three metres.
 */
data class Dimensions(
    val gutter: Dp,
    val railGap: Dp,
    val sectionGap: Dp,
    val posterWidth: Dp,
    val posterHeight: Dp,
    val posterWidthCompact: Dp,
    val posterHeightCompact: Dp,
    val heroHeight: Dp,
    val heroHeightCompact: Dp,
    /** The account portrait on the profile screen. */
    val avatarSize: Dp,
)

/** Phones and tablets, at arm's length. */
val TouchDimensions = Dimensions(
    gutter = 24.dp,
    railGap = 12.dp,
    sectionGap = 28.dp,
    posterWidth = 140.dp,
    posterHeight = 210.dp,
    posterWidthCompact = 108.dp,
    posterHeightCompact = 162.dp,
    heroHeight = 420.dp,
    heroHeightCompact = 280.dp,
    avatarSize = 96.dp,
)

/**
 * Ten-foot interface. The hero is cut to just over half the 540dp height so
 * the title and metadata stay on screen, and posters grow so a rail still
 * reads from across a room.
 */
val TvDimensions = Dimensions(
    gutter = 32.dp,
    railGap = 16.dp,
    sectionGap = 32.dp,
    posterWidth = 160.dp,
    posterHeight = 240.dp,
    posterWidthCompact = 160.dp,
    posterHeightCompact = 240.dp,
    heroHeight = 300.dp,
    heroHeightCompact = 300.dp,
    avatarSize = 120.dp,
)

val LocalDimensions = staticCompositionLocalOf { TouchDimensions }

/** True when running on a television, so callers can branch without a Context. */
val LocalIsTv = staticCompositionLocalOf { false }
