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

// Series status
val StatusOngoing = Color(0xFF75C71B)
val StatusEnded = Color(0xFFFF293D)

// Light-scheme counterparts, kept minimal — the app is intended to run dark.
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEFF2)
val LightOnSurface = Color(0xFF16181E)
val LightOnSurfaceMuted = Color(0xFF5A6070)
