package net.filmix.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Dark-first palette. A catalog of poster art needs a recessive ground, so the
 * brand orange is demoted from a surface colour (where the original app used it
 * for the status bar) to an accent reserved for actions and focus.
 */

// Brand
val FilmixOrange = Color(0xFFF26739)
val FilmixOrangeDark = Color(0xFFF05521)
val FilmixOrangeMuted = Color(0xFFE36236)

/**
 * Selected-state fill: chips, the nav rail's active pill, tonal icon buttons.
 * Material's untouched default here is a lavender that belongs to no part of
 * this palette. Deliberately a deep, low-chroma orange rather than the brand
 * tone — the focus ring is [FilmixOrange] at full strength, and a selected chip
 * filled with the same colour is exactly how focus became invisible on the
 * primary buttons.
 */
val SelectedContainer = Color(0xFF54291A)
val OnSelectedContainer = Color(0xFFFFD9C9)
val LightSelectedContainer = Color(0xFFFFE1D4)
val LightOnSelectedContainer = Color(0xFF41200F)

// Ground and surfaces
val Ink = Color(0xFF0E0F13)
val Surface1 = Color(0xFF16181E)
val Surface2 = Color(0xFF1E2128)
val Surface3 = Color(0xFF262A33)

// Text
val TextPrimary = Color(0xFFE8EAED)
val TextMuted = Color(0xFF8F95A3)
val TextFaint = Color(0xFF535865)

// Ratings and votes, carried over from the original so the app stays familiar
val ImdbGold = Color(0xFFE4AD2C)
val KinopoiskOrange = Color(0xFFF39300)
val VoteUp = Color(0xFF83BF45)
val VoteDown = Color(0xFFEE3442)

// Poster rating badge — the original app's rounded_positive / _negative /
// _neutral shape drawables, alpha and all.
val RatingPositive = Color(0x9889C71F)
val RatingNegative = Color(0x98EA383D)
val RatingNeutral = Color(0x989196A4)

// Series status
val StatusOngoing = Color(0xFF75C71B)
val StatusEnded = Color(0xFFFF293D)

// Light-scheme counterparts, kept minimal — the app is intended to run dark.
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEFF2)
val LightOnSurface = Color(0xFF16181E)
val LightOnSurfaceMuted = Color(0xFF5A6070)
