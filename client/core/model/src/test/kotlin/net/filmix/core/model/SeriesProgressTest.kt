package net.filmix.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesProgressTest {

    // Real link shape, including the rotating signed segment. Stored keys go
    // through StreamLink.resumeKey exactly the way ResumeStore writes them.
    private fun template(season: Int, episode: Int, translation: String) =
        "https://h/s/token/$translation/s${season}e${episode}_%s.mp4"

    private fun key(season: Int, episode: Int, translation: String) =
        StreamLink.resumeKey(template(season, episode, translation))

    private fun episode(season: Int, number: Int, translation: String) = Episode(
        number = number.toString(),
        source = VideoSource(translation, template(season, number, translation), listOf(720)),
    )

    private fun translation(season: Int, name: String, episodes: Int) =
        SeriesTranslation(name, (1..episodes).map { episode(season, it, name) })

    private fun season(number: Int, episodes: Int, vararg translations: String) =
        Season(number.toString(), translations.map { translation(number, it, episodes) })

    private fun finished(updatedAt: Long = 0) =
        WatchProgress(positionMs = 3_599_000, durationMs = 3_600_000, updatedAt = updatedAt)

    private fun inProgress(updatedAt: Long = 0) =
        WatchProgress(positionMs = 600_000, durationMs = 3_600_000, updatedAt = updatedAt)

    // --- WatchProgress boundaries ---

    @Test
    fun `unknown duration is never finished`() {
        assertFalse(WatchProgress(10_000_000, 0, 0).isFinished)
    }

    @Test
    fun `exactly sixty seconds before the end is not finished`() {
        assertFalse(WatchProgress(3_540_000, 3_600_000, 0).isFinished)
    }

    @Test
    fun `inside the final minute is finished`() {
        assertTrue(WatchProgress(3_540_001, 3_600_000, 0).isFinished)
    }

    @Test
    fun `episode resume key matches the key of any played quality`() {
        val e = episode(4, 1, "LostFilm")
        assertEquals(e.resumeKey, StreamLink.resumeKey(e.source.urlFor(720)))
        assertEquals(e.resumeKey, StreamLink.resumeKey(e.source.urlFor(2160)))
    }

    // --- seasonWatch ---

    private val lost = "LostFilm"
    private val dubl = "Dubl"

    private fun watch(
        s: Season = season(1, 4, lost, dubl),
        t: String = lost,
        progress: Map<String, WatchProgress>,
    ) = SeriesProgress.seasonWatch(s, s.translations.first { it.name == t }, progress)

    @Test
    fun `nothing watched - current is the first episode, no marks`() {
        val w = watch(progress = emptyMap())
        assertEquals("1", w.current?.number)
        assertFalse(w.currentInProgress)
        assertTrue(w.states.isEmpty())
    }

    @Test
    fun `an in-progress episode is the current one`() {
        val w = watch(progress = mapOf(key(1, 2, lost) to inProgress()))
        assertEquals("2", w.current?.number)
        assertTrue(w.currentInProgress)
        assertEquals(EpisodeWatchState.InProgress, w.states["2"])
    }

    @Test
    fun `with two in-progress episodes the freshest wins`() {
        val w = watch(
            progress = mapOf(
                key(1, 2, lost) to inProgress(updatedAt = 10),
                key(1, 3, lost) to inProgress(updatedAt = 20),
            ),
        )
        assertEquals("3", w.current?.number)
    }

    @Test
    fun `finished episodes advance current to the next`() {
        val w = watch(
            progress = mapOf(
                key(1, 1, lost) to finished(),
                key(1, 2, lost) to finished(),
                key(1, 3, lost) to finished(),
            ),
        )
        assertEquals("4", w.current?.number)
        assertFalse(w.currentInProgress)
        assertEquals(EpisodeWatchState.Finished, w.states["3"])
        assertNull(w.states["4"])
    }

    @Test
    fun `a hole does not stop advancing past the last finished`() {
        val w = watch(
            progress = mapOf(
                key(1, 1, lost) to finished(),
                key(1, 3, lost) to finished(),
            ),
        )
        assertEquals("4", w.current?.number)
    }

    @Test
    fun `all finished - current stays on the last episode`() {
        val w = watch(
            progress = (1..4).associate { key(1, it, lost) to finished() },
        )
        assertEquals("4", w.current?.number)
        assertFalse(w.currentInProgress)
    }

    @Test
    fun `a checkmark earned in another translation survives switching`() {
        val w = watch(t = dubl, progress = mapOf(key(1, 1, lost) to finished()))
        assertEquals(EpisodeWatchState.Finished, w.states["1"])
        assertEquals("2", w.current?.number)
    }

    @Test
    fun `an exact-key position beats a foreign finished mark`() {
        val w = watch(
            t = dubl,
            progress = mapOf(
                key(1, 1, lost) to finished(updatedAt = 20),
                key(1, 1, dubl) to inProgress(updatedAt = 10),
            ),
        )
        assertEquals(EpisodeWatchState.InProgress, w.states["1"])
        assertEquals("1", w.current?.number)
        assertTrue(w.currentInProgress)
    }

    @Test
    fun `a foreign finished mark is not hidden by a stale foreign half-watch`() {
        val third = translation(1, "Third", 4)
        val s = Season("1", listOf(translation(1, lost, 4), translation(1, dubl, 4), third))
        val w = SeriesProgress.seasonWatch(
            s,
            s.translations.first { it.name == lost },
            mapOf(
                key(1, 1, dubl) to finished(updatedAt = 10),
                key(1, 1, "Third") to inProgress(updatedAt = 20),
            ),
        )
        assertEquals(EpisodeWatchState.Finished, w.states["1"])
    }

    // --- resumePoint ---

    private val playlist = SeriesPlaylist(
        listOf(season(1, 4, lost, dubl), season(2, 4, lost, dubl)),
    )

    @Test
    fun `nothing stored means no resume point`() {
        assertNull(SeriesProgress.resumePoint(playlist, emptyMap()))
    }

    @Test
    fun `the freshest row pins season and translation`() {
        val point = SeriesProgress.resumePoint(
            playlist,
            mapOf(
                key(1, 4, lost) to finished(updatedAt = 10),
                key(2, 1, dubl) to inProgress(updatedAt = 20),
            ),
        )
        assertEquals(PlaylistPosition(season = "2", translation = dubl), point)
    }

    @Test
    fun `finishing the last episode of a season advances to the next`() {
        val point = SeriesProgress.resumePoint(
            playlist,
            mapOf(key(1, 4, dubl) to finished()),
        )
        assertEquals(PlaylistPosition(season = "2", translation = dubl), point)
    }

    @Test
    fun `finishing the last episode of the last season stays put`() {
        val point = SeriesProgress.resumePoint(
            playlist,
            mapOf(key(2, 4, lost) to finished()),
        )
        assertEquals(PlaylistPosition(season = "2", translation = lost), point)
    }

    @Test
    fun `advancing falls back to the next season's first translation`() {
        val shifted = SeriesPlaylist(listOf(season(1, 4, dubl), season(2, 4, lost)))
        val point = SeriesProgress.resumePoint(
            shifted,
            mapOf(key(1, 4, dubl) to finished()),
        )
        assertEquals(PlaylistPosition(season = "2", translation = lost), point)
    }

    @Test
    fun `a mid-season finish stays in its season`() {
        val point = SeriesProgress.resumePoint(
            playlist,
            mapOf(key(1, 2, lost) to finished()),
        )
        assertEquals(PlaylistPosition(season = "1", translation = lost), point)
    }
}
